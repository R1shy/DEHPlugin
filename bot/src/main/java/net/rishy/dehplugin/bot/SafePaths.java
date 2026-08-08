package net.rishy.dehplugin.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Path confinement helpers ported from mc-agent.py's safe_path / safe_leaf. */
public final class SafePaths {

    private SafePaths() {
    }

    /** Resolve relative inside root, refusing anything that escapes it. */
    public static Path safePath(Path root, String relative) throws IOException {
        Path target = root.resolve(relative.stripLeading().replaceFirst("^/+", "")).normalize().toAbsolutePath();
        Path rootNorm = root.toAbsolutePath().normalize();
        if (!target.equals(rootNorm) && !target.startsWith(rootNorm)) {
            throw new IOException("path escapes server directory: " + relative);
        }
        return target;
    }

    /** Locate something by name; the parent goes through safePath, the leaf is joined but not followed. */
    public static Path safeLeaf(Path root, String relative) throws IOException {
        String cleaned = relative.strip().stripLeading();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            throw new IOException("a path is required");
        }
        int slash = cleaned.lastIndexOf('/');
        String name = slash < 0 ? cleaned : cleaned.substring(slash + 1);
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IOException("invalid name: " + name);
        }
        String parentPart = slash < 0 ? "" : cleaned.substring(0, slash);
        Path parent = safePath(root, parentPart);
        if (!Files.isDirectory(parent)) {
            throw new IOException("not a directory: " + (parentPart.isEmpty() ? "/" : parentPart));
        }
        return parent.resolve(name);
    }

    /** Where one archive member is allowed to land. */
    public static Path memberTarget(Path destination, String name) throws IOException {
        String cleaned = name.replace('\\', '/');
        if (cleaned.startsWith("/")) {
            throw new IOException("archive member has an absolute path: " + name);
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String part : cleaned.split("/")) {
            if (!part.isEmpty() && !part.equals(".")) {
                parts.add(part);
            }
        }
        if (parts.contains("..")) {
            throw new IOException("archive member escapes the destination: " + name);
        }
        if (parts.isEmpty()) {
            throw new IOException("archive member has no name: " + name);
        }
        Path target = destination;
        for (String part : parts) {
            target = target.resolve(part);
        }
        if (!target.toAbsolutePath().normalize().startsWith(destination.toAbsolutePath().normalize())) {
            throw new IOException("archive member escapes the destination: " + name);
        }
        return target;
    }
}
