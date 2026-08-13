package net.rishy.dehplugin.dev;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Dev-only hot reload runner. Started by the {@code devServer} Gradle task.
 * Starts the Paper server, watches {@code src/}, and on every change rebuilds
 * the plugin shadow jar and drops it into the server's {@code plugins/} dir.
 */
public final class DevServer {

    private static final long POLL_MILLIS = 1000;
    private static final long DEBOUNCE_MILLIS = 250;
    private static final int DEBOUNCE_ROUNDS = 8;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("[hotreload] fatal: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path serverDir = Path.of(args[0]).toAbsolutePath().normalize();
        Path projectDir = Path.of(args[1]).toAbsolutePath().normalize();
        String startCommand = args.length > 2 && !args[2].isBlank()
                ? args[2]
                : defaultStartCommand(serverDir);

        System.out.println("[hotreload] server dir : " + serverDir);
        System.out.println("[hotreload] project dir: " + projectDir);
        System.out.println("[hotreload] start cmd  : " + startCommand);

        System.out.println("[hotreload] initial build...");
        build(projectDir);
        deploy(serverDir, projectDir);

        Process server = startServer(serverDir, startCommand);
        relayStdin(server);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[hotreload] stopping server (pid " + server.pid() + ")...");
            server.destroy();
            try {
                if (!server.waitFor(5, TimeUnit.SECONDS)) {
                    server.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.destroyForcibly();
            }
        }, "hotreload-shutdown"));

        Path srcRoot = projectDir.resolve("src");
        long fingerprint = fingerprint(srcRoot);
        System.out.println("[hotreload] watching " + srcRoot + " ...");
        System.out.println("[hotreload] type commands in this terminal to control the server (e.g. /reload)");

        boolean building = false;
        while (true) {
            Thread.sleep(POLL_MILLIS);
            long next = fingerprint(srcRoot);
            if (next == fingerprint || building) {
                continue;
            }
            building = true;
            try {
                fingerprint = debounce(srcRoot, next);
                System.out.println("[hotreload] change detected, rebuilding...");
                build(projectDir);
                deploy(serverDir, projectDir);
                System.out.println("[hotreload] run /reload in the server console to apply.");
            } finally {
                building = false;
            }
        }
    }

    private static long debounce(Path srcRoot, long initial) {
        long stable = initial;
        for (int i = 0; i < DEBOUNCE_ROUNDS; i++) {
            try {
                Thread.sleep(DEBOUNCE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return stable;
            }
            long cur = fingerprint(srcRoot);
            if (cur == stable) {
                return stable;
            }
            stable = cur;
        }
        return stable;
    }

    private static long fingerprint(Path root) {
        if (!Files.exists(root)) {
            return 0L;
        }
        long hash = 0L;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    hash = hash * 31 + Objects.hash(
                            file.toString(), attrs.lastModifiedTime().toMillis(), attrs.size());
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return hash;
    }

    private static void build(Path projectDir) throws IOException, InterruptedException {
        String gradlew = projectDir.resolve("gradlew").toString();
        Process p = new ProcessBuilder(gradlew, "shadowJar", "--console=plain")
                .directory(projectDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        if (!p.waitFor(10, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            throw new IOException("gradle build timed out after 10 minutes");
        }
        if (p.exitValue() != 0) {
            throw new IOException("gradle build failed (exit " + p.exitValue() + "), keeping old jar");
        }
    }

    private static void deploy(Path serverDir, Path projectDir) throws IOException {
        Path libs = projectDir.resolve("build/libs");
        Path newest = null;
        long newestTime = Long.MIN_VALUE;
        try (Stream<Path> files = Files.list(libs)) {
            for (Path jar : files.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                long t = Files.getLastModifiedTime(jar).toMillis();
                if (t > newestTime) {
                    newestTime = t;
                    newest = jar;
                }
            }
        }
        if (newest == null) {
            throw new IOException("no plugin jar found in " + libs);
        }

        Path plugins = serverDir.resolve("plugins");
        Files.createDirectories(plugins);
        try (Stream<Path> files = Files.list(plugins)) {
            for (Path stale : files.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("DEHPlugin") && name.endsWith(".jar");
            }).toList()) {
                Files.deleteIfExists(stale);
            }
        }

        Path dest = plugins.resolve("DEHPlugin.jar");
        Files.copy(newest, dest, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[hotreload] deployed " + newest.getFileName() + " -> " + dest);
    }

    private static Process startServer(Path serverDir, String command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c",
                "cd '" + serverDir + "' && " + command);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        System.out.println("[hotreload] server started (pid " + p.pid() + ")");
        return p;
    }

    private static String defaultStartCommand(Path serverDir) throws IOException {
        List<Path> jars;
        try (Stream<Path> files = Files.list(serverDir)) {
            jars = files.filter(p -> p.getFileName().toString().endsWith(".jar")).toList();
        }
        Path server = null;
        long newest = Long.MIN_VALUE;
        for (Path jar : jars) {
            if (!jar.getFileName().toString().toLowerCase().startsWith("paper")) {
                continue;
            }
            long t = Files.getLastModifiedTime(jar).toMillis();
            if (t > newest) {
                newest = t;
                server = jar;
            }
        }
        if (server == null) {
            newest = Long.MIN_VALUE;
            for (Path jar : jars) {
                long t = Files.getLastModifiedTime(jar).toMillis();
                if (t > newest) {
                    newest = t;
                    server = jar;
                }
            }
        }
        if (server == null) {
            throw new IOException("no server jar found in " + serverDir
                    + " (drop a paper jar there or set -PdevStartCommand)");
        }
        return "java -jar " + server.getFileName() + " nogui";
    }

    private static void relayStdin(Process server) {
        Thread relay = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                OutputStream stdin = server.getOutputStream();
                String line;
                while ((line = reader.readLine()) != null) {
                    stdin.write((line + "\n").getBytes());
                    stdin.flush();
                }
            } catch (IOException ignored) {
            }
        }, "hotreload-stdin");
        relay.setDaemon(true);
        relay.start();
    }
}
