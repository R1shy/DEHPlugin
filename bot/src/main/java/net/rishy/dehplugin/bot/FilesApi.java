package net.rishy.dehplugin.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** File access, ported from mc-agent.py with its limits and guards. */
public final class FilesApi {

    public static final int MAX_READ_BYTES = 256 * 1024;
    public static final int MAX_WRITE_BYTES = 1024 * 1024;
    public static final int MAX_ARCHIVE_ENTRIES = 20_000;
    public static final long FREE_SPACE_MARGIN = 64L * 1024 * 1024;

    private final long uploadLimit;

    public FilesApi(BotConfig config) {
        this.uploadLimit = Math.max(config.getInt("max_upload_mb", 512), 1) * 1024L * 1024L;
    }

    public long uploadLimit() {
        return uploadLimit;
    }

    public record Entry(String name, String path, boolean dir, boolean link, long size, long modified) {}

    public String list(Path root, String relative) throws IOException {
        Path target = SafePaths.safePath(root, relative);
        if (!Files.isDirectory(target)) {
            throw new IOException("not a directory: " + (relative.isEmpty() ? "/" : relative));
        }
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(target)) {
            stream.sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p)).thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(child -> {
                        try {
                            var attr = Files.readAttributes(child, "posix:size,lastModifiedTime,isSymbolicLink", java.nio.file.LinkOption.NOFOLLOW_LINKS);
                            boolean link = (Boolean) attr.get("isSymbolicLink");
                            Path normalized = child.toAbsolutePath().normalize();
                            String path = root.toAbsolutePath().normalize().relativize(normalized).toString();
                            entries.add(new Entry(child.getFileName().toString(), path,
                                    Files.isDirectory(child), link,
                                    (Long) attr.get("size"), ((java.nio.file.attribute.FileTime) attr.get("lastModifiedTime")).toMillis()));
                        } catch (IOException ignored) {
                        }
                    });
        }
        StringBuilder sb = new StringBuilder();
        long free = 0;
        long total = 0;
        try {
            FileStore store = Files.getFileStore(target);
            free = store.getUsableSpace();
            total = store.getTotalSpace();
        } catch (IOException ignored) {
        }
        sb.append("Directory `").append(relative.isEmpty() ? "/" : relative).append("`\n");
        if (entries.isEmpty()) {
            sb.append("(empty)\n");
        }
        for (Entry entry : entries) {
            sb.append(entry.dir() ? "📁 " : "📄 ").append(entry.link() ? "🔗 " : "")
                    .append('`').append(entry.name()).append('`')
                    .append("  ").append(humanSize(entry.size()))
                    .append('\n');
        }
        sb.append("\nFree disk: ").append(humanSize(free)).append(" / ").append(humanSize(total));
        return sb.toString();
    }

    public record FileRead(String path, String content, long size) {}

    public FileRead read(Path root, String relative) throws IOException {
        Path target = SafePaths.safePath(root, relative);
        if (!Files.isRegularFile(target)) {
            throw new IOException("not a file: " + relative);
        }
        long size = Files.size(target);
        if (size > MAX_READ_BYTES) {
            throw new IOException("file is " + size + " bytes; only text files under 256 KB can be viewed");
        }
        byte[] blob = Files.readAllBytes(target);
        for (int i = 0; i < Math.min(blob.length, 8192); i++) {
            if (blob[i] == 0) {
                throw new IOException("this is a binary file — download it instead of opening it");
            }
        }
        return new FileRead(relative, new String(blob, StandardCharsets.UTF_8), size);
    }

    public long size(Path root, String relative) throws IOException {
        Path target = SafePaths.safePath(root, relative);
        if (!Files.isRegularFile(target)) {
            throw new IOException("not a file: " + relative);
        }
        return Files.size(target);
    }

    public byte[] download(Path root, String relative) throws IOException {
        Path target = SafePaths.safePath(root, relative);
        if (!Files.isRegularFile(target)) {
            throw new IOException("not a file: " + relative);
        }
        long size = Files.size(target);
        if (size > 25L * 1024 * 1024) {
            throw new IOException("file is " + humanSize(size) + " — Discord allows attachments up to 25 MB, so it is not sent");
        }
        return Files.readAllBytes(target);
    }

    public Result write(Path root, String relative, String content, boolean overwrite) throws IOException {
        Path target = SafePaths.safeLeaf(root, relative);
        if (Files.isSymbolicLink(target)) {
            throw new IOException("refusing to write through a symlink");
        }
        if (Files.isDirectory(target)) {
            throw new IOException("is a directory: " + relative);
        }
        if (Files.exists(target) && !overwrite) {
            throw new IOException(relative + " already exists");
        }
        byte[] blob = content.getBytes(StandardCharsets.UTF_8);
        if (blob.length > MAX_WRITE_BYTES) {
            throw new IOException("content is " + blob.length + " bytes; the editor tops out at " + MAX_WRITE_BYTES);
        }
        writeAtomically(target, blob);
        return Result.ok("wrote " + relative + " (" + blob.length + " bytes)");
    }

    public Result upload(Path root, String relative, byte[] blob, boolean overwrite) throws IOException {
        Path target = SafePaths.safeLeaf(root, relative);
        if (Files.isSymbolicLink(target)) {
            throw new IOException("refusing to write through a symlink");
        }
        if (Files.isDirectory(target)) {
            throw new IOException("is a directory: " + relative);
        }
        if (Files.exists(target) && !overwrite) {
            throw new IOException(relative + " already exists");
        }
        if (blob.length > uploadLimit) {
            throw new IOException("upload is " + blob.length + " bytes; this bot accepts at most " + uploadLimit);
        }
        long free = Files.getFileStore(target.getParent()).getUsableSpace();
        if (blob.length + FREE_SPACE_MARGIN > free) {
            throw new IOException("not enough room: " + free + " bytes free");
        }
        writeAtomically(target, blob);
        return Result.ok("uploaded " + relative + " (" + blob.length + " bytes)");
    }

    private static void writeAtomically(Path target, byte[] blob) throws IOException {
        if (Files.exists(target)) {
            Path temp = target.resolveSibling("." + target.getFileName() + ".dashboard-tmp");
            Files.write(temp, blob);
            try {
                try {
                    Files.setAttribute(temp, "posix:permissions",
                            Files.getAttribute(target, "posix:permissions"));
                } catch (IOException ignored) {
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
        } else {
            Files.write(target, blob);
        }
    }

    public Result mkdir(Path root, String relative) throws IOException {
        Path target = SafePaths.safeLeaf(root, relative);
        if (Files.exists(target) || Files.isSymbolicLink(target)) {
            throw new IOException(relative + " already exists");
        }
        Files.createDirectories(target);
        return Result.ok("created directory " + relative);
    }

    public Result move(Path root, String relative, String destination) throws IOException {
        Path source = SafePaths.safeLeaf(root, relative);
        if (!Files.exists(source) && !Files.isSymbolicLink(source)) {
            throw new IOException("not found: " + relative);
        }
        Path target = SafePaths.safeLeaf(root, destination);
        if (Files.exists(target) || Files.isSymbolicLink(target)) {
            throw new IOException(destination + " already exists");
        }
        Files.move(source, target);
        return Result.ok("moved " + relative + " -> " + destination);
    }

    public Result delete(Path root, List<String> relatives) throws IOException {
        List<String> removed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String relative : relatives) {
            try {
                Path target = SafePaths.safeLeaf(root, relative);
                if (Files.isDirectory(target) && !Files.isSymbolicLink(target)) {
                    try (Stream<Path> walk = Files.walk(target)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                failed.add(relative + ": " + e.getMessage());
                            }
                        });
                    }
                } else {
                    Files.deleteIfExists(target);
                }
                removed.add(relative);
            } catch (Exception e) {
                failed.add(relative + ": " + e.getMessage());
            }
        }
        if (failed.isEmpty()) {
            return Result.ok("deleted: " + String.join(", ", removed));
        }
        return Result.err("deleted: " + String.join(", ", removed) + "\nfailed: " + String.join("\n", failed));
    }

    public Result extract(Path root, String relative, String destinationRelative) throws IOException {
        Path archive = SafePaths.safePath(root, relative);
        if (!Files.isRegularFile(archive)) {
            throw new IOException("not a file: " + relative);
        }
        if (!archive.getFileName().toString().toLowerCase().endsWith(".zip")) {
            throw new IOException(archive.getFileName() + " is not a .zip file");
        }
        Path destination = SafePaths.safeLeaf(root, destinationRelative);
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("refusing to extract through a symlink");
        }
        if (Files.exists(destination) && !Files.isDirectory(destination)) {
            throw new IOException("not a directory: " + destinationRelative);
        }
        try (org.apache.commons.compress.archivers.zip.ZipFile bundle =
                     new org.apache.commons.compress.archivers.zip.ZipFile(archive)) {
            java.util.Enumeration<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> enumeration = bundle.getEntries();
            List<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> members = new ArrayList<>();
            enumeration.asIterator().forEachRemaining(members::add);
            if (members.size() > MAX_ARCHIVE_ENTRIES) {
                throw new IOException("archive holds " + members.size() + " entries; this bot extracts at most " + MAX_ARCHIVE_ENTRIES);
            }
            long declared = 0;
            for (org.apache.commons.compress.archivers.zip.ZipArchiveEntry member : members) {
                if ((member.getUnixMode() & 0xF000) == 0xA000) {
                    throw new IOException("archive contains a symlink: " + member.getName());
                }
                declared += member.getSize();
            }
            if (declared > uploadLimit) {
                throw new IOException("archive expands to " + declared + " bytes; this bot accepts at most " + uploadLimit);
            }
            long free = Files.getFileStore(destination).getUsableSpace();
            if (declared + FREE_SPACE_MARGIN > free) {
                throw new IOException("not enough room: " + free + " bytes free, " + declared + " needed");
            }
            Files.createDirectories(destination);
            int files = 0;
            long written = 0;
            for (org.apache.commons.compress.archivers.zip.ZipArchiveEntry member : members) {
                Path target = SafePaths.memberTarget(destination, member.getName());
                if (member.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                if (Files.isSymbolicLink(target)) {
                    throw new IOException("refusing to overwrite a symlink: " + member.getName());
                }
                try (var in = bundle.getInputStream(member);
                     var out = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE,
                             java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.APPEND)) {
                    byte[] buffer = new byte[256 * 1024];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        written += read;
                        if (written > uploadLimit) {
                            throw new IOException("archive expands past the " + uploadLimit + " byte limit");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                files++;
            }
            return Result.ok("extracted " + files + " files (" + written + " bytes) to " + destinationRelative);
        }
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.1f %s", value, units[unit]);
    }
}
