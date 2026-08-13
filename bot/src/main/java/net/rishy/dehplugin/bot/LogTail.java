package net.rishy.dehplugin.bot;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LogTail {

    private final Path path;
    private RandomAccessFile handle;
    private long inode = -1;

    public LogTail(Path serverDir) {
        this.path = serverDir.resolve("logs/latest.log");
    }

    private void reopen() throws IOException {
        close();
        if (!Files.exists(path)) {
            return;
        }
        handle = new RandomAccessFile(path.toFile(), "r");
        inode = ((Number) Files.readAttributes(path, "unix:ino",
                java.nio.file.LinkOption.NOFOLLOW_LINKS).get("ino")).longValue();
        handle.seek(handle.length());
    }

    private void close() throws IOException {
        if (handle != null) {
            handle.close();
            handle = null;
        }
    }

    public List<String> readNew() {
        List<String> lines = new ArrayList<>();
        try {
            if (handle == null) {
                reopen();
                return lines;
            }
            long currentInode = ((Number) Files.readAttributes(path, "unix:ino",
                    java.nio.file.LinkOption.NOFOLLOW_LINKS).get("ino")).longValue();
            long length = Files.size(path);
            if (currentInode != inode || length < handle.getFilePointer()) {
                reopen();
                if (handle != null) {
                    handle.seek(0);
                }
            }
            if (handle == null) {
                return lines;
            }
            byte[] buffer = new byte[(int) (length - handle.getFilePointer())];
            handle.readFully(buffer);
            String file = new String(buffer);
            for (String line : file.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
            return lines.size() > 200 ? lines.subList(lines.size() - 200, lines.size()) : lines;
        } catch (IOException e) {
            return lines;
        }
    }

    public static List<String> tail(Path serverDir, int maxLines) throws IOException {
        Path path = serverDir.resolve("logs/latest.log");
        if (!Files.exists(path)) {
            return List.of();
        }
        byte[] bytes = Files.readAllBytes(path);
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(List.of(text.split("\n")));
        lines.removeIf(String::isBlank);
        if (lines.size() > maxLines) {
            lines = lines.subList(lines.size() - maxLines, lines.size());
        }
        return lines;
    }

    public static List<String> head(Path serverDir, int maxLines) throws IOException {
        Path path = serverDir.resolve("logs/latest.log");
        if (!Files.exists(path)) {
            return List.of();
        }
        byte[] bytes = Files.readAllBytes(path);
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(List.of(text.split("\n")));
        lines.removeIf(String::isBlank);
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        return lines;
    }
}
