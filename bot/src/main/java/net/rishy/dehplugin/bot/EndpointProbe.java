package net.rishy.dehplugin.bot;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reports the public addresses a tunnel currently maps this machine to (best effort). */
public final class EndpointProbe {

    private static final long CACHE_SECONDS = 60;
    private static final int TIMEOUT_SECONDS = 2;
    private static final int MAX_BYTES = 256 * 1024;

    private final String api;
    private final Map<String, String> manual = new LinkedHashMap<>();
    private final Map<String, Integer> localPorts = new LinkedHashMap<>();
    private Map<String, String> cached = Map.of();
    private long fetched = 0;

    public EndpointProbe(BotConfig config) {
        this.api = config.get("tunnel_api", "").strip();
        putManual("mc", config.get("public_mc"));
        putManual("rcon", config.get("public_rcon"));
        localPorts.put("mc", config.getInt("server_port", 25565));
        localPorts.put("rcon", config.getInt("rcon_port", 25575));
    }

    private void putManual(String key, String value) {
        if (value != null && !value.isBlank()) {
            manual.put(key, value.strip());
        }
    }

    public Map<String, String> current() {
        Map<String, String> found = new LinkedHashMap<>(manual);
        for (Map.Entry<String, String> discovered : discover().entrySet()) {
            found.putIfAbsent(discovered.getKey(), discovered.getValue());
        }
        return found;
    }

    private Map<String, String> discover() {
        long now = System.currentTimeMillis() / 1000;
        if (now - fetched < CACHE_SECONDS) {
            return cached;
        }
        fetched = now;
        cached = Map.of();
        if (api.isEmpty()) {
            return cached;
        }
        String payload;
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(api).toURL().openConnection();
            connection.setConnectTimeout(TIMEOUT_SECONDS * 1000);
            connection.setReadTimeout(TIMEOUT_SECONDS * 1000);
            payload = new String(connection.getInputStream().readNBytes(MAX_BYTES));
        } catch (Exception e) {
            return cached;
        }
        Map<String, String> result = new LinkedHashMap<>();
        String[] tunnels = extractTunnels(payload);
        for (String tunnel : tunnels) {
            String proto = jsonField(tunnel, "proto");
            if (!"tcp".equals(proto)) {
                continue;
            }
            String publicUrl = jsonField(tunnel, "public_url");
            String publicAddr = publicUrl.contains("//")
                    ? publicUrl.substring(publicUrl.indexOf("//") + 2) : publicUrl;
            String forwards = jsonField(jsonField(tunnel, "config"), "addr");
            String localPort = forwards.substring(forwards.lastIndexOf(':') + 1);
            if (publicAddr.isEmpty() || !localPort.matches("\\d+")) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : localPorts.entrySet()) {
                if (Integer.parseInt(localPort) == entry.getValue()) {
                    result.put(entry.getKey(), publicAddr);
                }
            }
        }
        cached = result;
        return cached;
    }

    private static String[] extractTunnels(String payload) {
        String start = "\"tunnels\":[";
        int idx = payload.indexOf(start);
        if (idx < 0) {
            return new String[0];
        }
        int open = payload.indexOf('[', idx);
        int close = payload.indexOf(']', open);
        if (close < 0) {
            return new String[0];
        }
        String body = payload.substring(open + 1, close);
        java.util.List<String> objects = new java.util.ArrayList<>();
        int depth = 0;
        int objectStart = -1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                if (depth++ == 0) {
                    objectStart = i;
                }
            } else if (c == '}') {
                if (--depth == 0 && objectStart >= 0) {
                    objects.add(body.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }
        return objects.toArray(new String[0]);
    }

    private static String jsonField(String object, String key) {
        String needle = "\"" + key + "\":";
        int idx = object.indexOf(needle);
        if (idx < 0) {
            return "";
        }
        int valueStart = idx + needle.length();
        char first = object.charAt(valueStart);
        if (first == '"') {
            int end = object.indexOf('"', valueStart + 1);
            return end < 0 ? "" : object.substring(valueStart + 1, end);
        }
        int end = object.indexOf(',', valueStart);
        if (end < 0) {
            end = object.indexOf('}', valueStart);
        }
        return end < 0 ? object.substring(valueStart) : object.substring(valueStart, end).strip();
    }
}
