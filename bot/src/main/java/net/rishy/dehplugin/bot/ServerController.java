package net.rishy.dehplugin.bot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Starts/stops/inspects the server process, whatever form it takes. */
public final class ServerController {

    public record CommandResult(int code, String output) {}

    private final BotConfig config;
    private final String mode;
    private final Path serverDir;
    private final EndpointProbe endpoints;
    private Process managed;
    private final Object lock = new Object();

    public ServerController(BotConfig config) {
        this.config = config;
        this.serverDir = Path.of(config.get("server_dir", "")).toAbsolutePath().normalize();
        this.endpoints = new EndpointProbe(config);
        this.mode = detectMode(config.get("mode", "auto"));
    }

    public String mode() {
        return mode;
    }

    public Path serverDir() {
        return serverDir;
    }

    private String detectMode(String requested) {
        if (!requested.equals("auto")) {
            return requested;
        }
        CommandResult systemctl = run(List.of("systemctl", "list-unit-files",
                config.get("service_name", "minecraft") + ".service"), 20);
        if (systemctl.code() == 0) {
            return "systemd";
        }
        CommandResult docker = run(List.of("docker", "ps", "-a", "--format", "{{.Names}}"), 20);
        if (docker.code() == 0 && docker.output().contains(config.get("container", "minecraft"))) {
            return "docker";
        }
        CommandResult screen = run(List.of("screen", "-ls"), 20);
        if (screen.code() == 0 && screen.output().contains(config.get("screen_name", "minecraft"))) {
            return "screen";
        }
        return "managed";
    }

    static CommandResult run(List<String> args, long timeoutSeconds) {
        try {
            ProcessBuilder builder = new ProcessBuilder(args);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(124, "timed out after " + timeoutSeconds + "s");
            }
            return new CommandResult(process.exitValue(), output.strip());
        } catch (IOException e) {
            return new CommandResult(127, "command not found: " + args.getFirst());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(130, "interrupted");
        }
    }

    public boolean isRunning() {
        return switch (mode) {
            case "systemd" -> run(List.of("systemctl", "is-active", "--quiet",
                    config.get("service_name", "minecraft")), 20).code() == 0;
            case "docker" -> run(List.of("docker", "inspect", "-f", "{{.State.Running}}",
                    config.get("container", "minecraft")), 20).output().equals("true");
            case "screen" -> run(List.of("screen", "-ls"), 20).output()
                    .contains(config.get("screen_name", "minecraft"));
            default -> isManagedRunning();
        };
    }

    private boolean isManagedRunning() {
        synchronized (lock) {
            return managed != null && managed.isAlive();
        }
    }

    public Result start() {
        synchronized (lock) {
            if (isRunning()) {
                return Result.ok("already running");
            }
            return switch (mode) {
                case "systemd" -> {
                    CommandResult r = run(List.of("systemctl", "start",
                            config.get("service_name", "minecraft")), 120);
                    yield new Result(r.code() == 0, r.output().isEmpty() ? "started" : r.output());
                }
                case "docker" -> {
                    CommandResult r = run(List.of("docker", "start",
                            config.get("container", "minecraft")), 120);
                    yield new Result(r.code() == 0, r.output().isEmpty() ? "started" : r.output());
                }
                case "screen" -> {
                    String command = config.get("start_command", "");
                    if (command.isEmpty()) {
                        yield Result.err("start_command is required for screen mode");
                    }
                    CommandResult r = run(List.of("screen", "-dmS",
                            config.get("screen_name", "minecraft"), "bash", "-c",
                            "cd " + serverDir + " && " + command), 20);
                    yield new Result(r.code() == 0, r.output().isEmpty() ? "started" : r.output());
                }
                default -> {
                    String command = config.get("start_command", "");
                    if (command.isEmpty()) {
                        yield Result.err("start_command is required for managed mode");
                    }
                    try {
                        ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", command);
                        builder.directory(serverDir.toFile());
                        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                        managed = builder.start();
                        yield Result.ok("started (pid " + managed.pid() + ")");
                    } catch (IOException e) {
                        yield Result.err(e.getMessage());
                    }
                }
            };
        }
    }

    public Result stop() {
        synchronized (lock) {
            if (!isRunning()) {
                return Result.ok("already stopped");
            }
            return switch (mode) {
                case "systemd" -> {
                    CommandResult r = run(List.of("systemctl", "stop",
                            config.get("service_name", "minecraft")), 120);
                    yield new Result(r.code() == 0, r.output().isEmpty() ? "stopped" : r.output());
                }
                case "docker" -> {
                    CommandResult r = run(List.of("docker", "stop", "-t", "60",
                            config.get("container", "minecraft")), 120);
                    yield new Result(r.code() == 0, r.output().isEmpty() ? "stopped" : r.output());
                }
                case "screen" -> {
                    run(List.of("screen", "-S", config.get("screen_name", "minecraft"),
                            "-X", "stuff", "stop\n"), 20);
                    for (int i = 0; i < 60; i++) {
                        if (!isRunning()) {
                            break;
                        }
                        sleep(1000);
                    }
                    if (isRunning()) {
                        yield Result.err("server did not stop within 60s");
                    }
                    yield Result.ok("stopped");
                }
                default -> stopManaged();
            };
        }
    }

    private Result stopManaged() {
        if (managed == null) {
            return Result.err("no managed process to stop");
        }
        OutputStream stdin = managed.getOutputStream();
        if (stdin != null) {
            try {
                stdin.write("stop\n".getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (IOException ignored) {
            }
        }
        try {
            if (managed.waitFor(60, TimeUnit.SECONDS)) {
                managed = null;
                return Result.ok("stopped");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        managed.destroyForcibly();
        managed = null;
        return Result.ok("killed after graceful stop timed out");
    }

    public Result restart() {
        Result stop = stop();
        if (!stop.ok()) {
            return Result.err("stop failed: " + stop.detail());
        }
        sleep(3000);
        return start();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, Object> state() {
        Map<String, Object> info = new LinkedHashMap<>();
        boolean running = isRunning();
        info.put("mode", mode);
        info.put("running", running);
        info.put("server_dir", serverDir.toString());
        info.put("version", "1.0.0");
        info.put("has_properties", Files.isRegularFile(serverDir.resolve("server.properties")));
        Path log = serverDir.resolve("logs/latest.log");
        try {
            info.put("log_age", Math.max(System.currentTimeMillis() - Files.getLastModifiedTime(log).toMillis(), 0));
        } catch (IOException e) {
            info.put("log_age", null);
        }
        Map<String, String> endpointsFound = endpoints.current();
        if (!endpointsFound.isEmpty()) {
            info.put("endpoints", endpointsFound);
        }
        try {
            long free = serverDir.toFile().getUsableSpace();
            long total = serverDir.toFile().getTotalSpace();
            info.put("disk_free", free);
            info.put("disk_total", total);
        } catch (Exception ignored) {
        }
        if (running && mode.equals("docker")) {
            CommandResult r = run(List.of("docker", "stats", "--no-stream", "--format",
                    "{{.CPUPerc}}|{{.MemUsage}}", config.get("container", "minecraft")), 30);
            if (r.output().contains("|")) {
                String[] parts = r.output().split("\\|", 2);
                info.put("cpu", parts[0].strip());
                info.put("memory", parts[1].strip());
            }
        } else if (running && mode.equals("systemd")) {
            CommandResult r = run(List.of("systemctl", "show", config.get("service_name", "minecraft"),
                    "--property=MainPID", "--property=ActiveEnterTimestamp"), 20);
            for (String line : r.output().split("\n")) {
                if (line.startsWith("MainPID=")) {
                    info.put("pid", line.substring("MainPID=".length()));
                } else if (line.startsWith("ActiveEnterTimestamp=")) {
                    info.put("since", line.substring("ActiveEnterTimestamp=".length()));
                }
            }
        }
        return info;
    }
}
