package net.rishy.dehplugin.bot;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** World backups, stored on the host and never uploaded to Discord. */
public final class BackupsApi {

    private final BotConfig config;
    private final Path backupDir;

    public BackupsApi(BotConfig config, Path serverDir) {
        this.config = config;
        this.backupDir = Path.of(config.get("backup_dir",
                serverDir.getParent().resolve("backups").toString()));
    }

    public Path backupDir() {
        return backupDir;
    }

    public record BackupInfo(String name, long size, long modified) {}

    public record BackupResult(String archive, long size, List<String> worlds, List<String> pruned) {}

    public BackupResult create(Path serverDir) throws IOException {
        Files.createDirectories(backupDir);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault()).format(Instant.now());
        Path archive = backupDir.resolve("world-" + stamp + ".tar.gz");

        List<Path> worlds = new ArrayList<>();
        try (Stream<Path> children = Files.list(serverDir)) {
            children.forEach(p -> {
                if (Files.isDirectory(p) && Files.exists(p.resolve("level.dat"))) {
                    worlds.add(p);
                }
            });
        }
        if (worlds.isEmpty()) {
            try (Stream<Path> children = Files.list(serverDir)) {
                children.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("world"))
                        .forEach(worlds::add);
            }
        }
        if (worlds.isEmpty()) {
            throw new IOException("no world directories found in server_dir");
        }

        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(
                new GzipCompressorOutputStream(Files.newOutputStream(archive)))) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            for (Path world : worlds) {
                addToTar(tar, world, world.getFileName().toString());
            }
        }

        int keep = config.getInt("backup_keep", 7);
        List<String> pruned = new ArrayList<>();
        try (Stream<Path> existing = Files.list(backupDir)) {
            List<Path> backups = existing.filter(p -> p.getFileName().toString().startsWith("world-")
                            && p.getFileName().toString().endsWith(".tar.gz"))
                    .sorted(Comparator.comparingLong(this::modified))
                    .toList();
            int limit = keep > 0 ? keep : 0;
            for (int i = 0; i < backups.size() - limit; i++) {
                Files.deleteIfExists(backups.get(i));
                pruned.add(backups.get(i).getFileName().toString());
            }
        }

        return new BackupResult(archive.getFileName().toString(), Files.size(archive),
                worlds.stream().map(p -> p.getFileName().toString()).toList(), pruned);
    }

    private void addToTar(TarArchiveOutputStream tar, Path path, String name) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setModTime(Files.getLastModifiedTime(path).toMillis());
        if (Files.isDirectory(path)) {
            entry.setSize(0);
            tar.putArchiveEntry(entry);
            tar.closeArchiveEntry();
            try (Stream<Path> children = Files.list(path)) {
                for (Path child : children.toList()) {
                    addToTar(tar, child, name + "/" + child.getFileName());
                }
            }
        } else {
            entry.setSize(Files.size(path));
            tar.putArchiveEntry(entry);
            Files.copy(path, tar);
            tar.closeArchiveEntry();
        }
    }

    private long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    public List<BackupInfo> list() throws IOException {
        if (!Files.isDirectory(backupDir)) {
            return List.of();
        }
        List<BackupInfo> items = new ArrayList<>();
        try (Stream<Path> existing = Files.list(backupDir)) {
            existing.filter(p -> p.getFileName().toString().startsWith("world-")
                            && p.getFileName().toString().endsWith(".tar.gz"))
                    .sorted(Comparator.comparingLong(this::modified).reversed())
                    .forEach(p -> {
                        try {
                            items.add(new BackupInfo(p.getFileName().toString(), Files.size(p), modified(p)));
                        } catch (IOException ignored) {
                        }
                    });
        }
        return items;
    }

    public static String formatTime(long millis) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
    }
}
