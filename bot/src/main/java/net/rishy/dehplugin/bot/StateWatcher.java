package net.rishy.dehplugin.bot;

import net.dv8tion.jda.api.JDA;

/** Announces server start/stop transitions to the bot channel. */
public final class StateWatcher implements Runnable {

    private static final long POLL_MS = 5000;

    private final JDA jda;
    private final BotConfig config;
    private final ServerController controller;
    private volatile boolean running = true;
    private Boolean lastState;

    public StateWatcher(JDA jda, BotConfig config, ServerController controller) {
        this.jda = jda;
        this.config = config;
        this.controller = controller;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                boolean nowRunning = controller.isRunning();
                if (lastState != null && lastState != nowRunning) {
                    announce(nowRunning ? "🟢 **Server started**" : "🛑 **Server stopped**");
                }
                lastState = nowRunning;
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

    private void announce(String message) {
        String channelName = config.get("bot_channel", "");
        if (channelName.isBlank()) {
            return;
        }
        jda.getGuildsByName(config.get("bot_server", ""), false).stream()
                .findFirst()
                .flatMap(guild -> guild.getTextChannelsByName(channelName, false).stream().findFirst())
                .ifPresent(channel -> channel.sendMessage(message).queue());
    }
}
