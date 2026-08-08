package net.rishy.dehplugin.bot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** INI-style config (key = value, # and ; comments), mirroring mc-agent.py's loader. */
public final class BotConfig {

    private final Map<String, String> values = new HashMap<>();

    public static BotConfig load(String path) throws IOException {
        BotConfig config = new BotConfig();
        Path file = Path.of(path);
        if (Files.exists(file)) {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.split(";", 2)[0].trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int eq = line.indexOf('=');
                config.values.put(line.substring(0, eq).trim().toLowerCase(),
                        line.substring(eq + 1).trim());
            }
        }
        return config;
    }

    public String get(String key) {
        return values.get(key);
    }

    public String get(String key, String def) {
        return Optional.ofNullable(values.get(key)).filter(s -> !s.isBlank()).orElse(def);
    }

    public boolean getBool(String key, boolean def) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        return value.toLowerCase().matches("1|yes|y|true|on|enabled");
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public List<String> getList(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
