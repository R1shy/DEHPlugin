package net.rishy.dehplugin.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.List;

/** Streams server log lines to the configured log channel, coalesced and rate-limited. */
public final class LogForwarder implements Runnable {

    private static final long POLL_MS = 500;
    private static final long FLUSH_MS = 2000;
    private static final int MAX_MESSAGE = 1500;

    private final JDA jda;
    private final BotConfig config;
    private final ServerController controller;
    private final LogTail tail;
    private volatile boolean running = true;

    public LogForwarder(JDA jda, BotConfig config, ServerController controller) {
        this.jda = jda;
        this.config = config;
        this.controller = controller;
        this.tail = new LogTail(controller.serverDir());
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        long lastFlush = 0;
        List<String> pending = new ArrayList<>();
        while (running) {
            long now = System.currentTimeMillis();
            try {
                List<String> fresh = tail.readNew();
                for (String line : fresh) {
                    if (isSkippable(line)) {
                        continue;
                    }
                    String formatted = LogFormatter.format(line);
                    if (formatted != null) {
                        pending.add(formatted);
                    }
                }
                if (!pending.isEmpty() && now - lastFlush >= FLUSH_MS) {
                    lastFlush = now;
                    flush(new ArrayList<>(pending));
                    pending.clear();
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void flush(List<String> lines) {
        TextChannel channel = findChannel(config.get("log_channel", ""));
        if (channel == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        for (String line : lines) {
            if (message.length() + line.length() + 1 > MAX_MESSAGE) {
                send(channel, message.toString());
                message.setLength(0);
            }
            message.append(line).append('\n');
        }
        if (!message.isEmpty()) {
            send(channel, message.toString());
        }
    }

    private void send(TextChannel channel, String text) {
        String content = text.strip();
        if (content.length() > MAX_MESSAGE) {
            content = content.substring(content.length() - MAX_MESSAGE) + "\n…";
        }
        if (!content.isEmpty()) {
            channel.sendMessage(content).queue();
        }
    }

    private TextChannel findChannel(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return jda.getGuildsByName(config.get("bot_server", ""), false).stream()
                .findFirst()
                .flatMap(guild -> guild.getTextChannelsByName(name, false).stream().findFirst())
                .orElse(null);
    }

    private static boolean isSkippable(String line) {
        return line.contains("Thread RCON Client")
                && (line.endsWith("started") || line.endsWith("shutting down"));
    }
}
