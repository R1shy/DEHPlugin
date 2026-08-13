package net.rishy.dehplugin.bot;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Forwards Discord chat messages from a designated channel into the game. */
public final class GameChatBridge extends ListenerAdapter {

    private static final int MAX_LENGTH = 256;

    private final BotConfig config;
    private final ServerController controller;

    public GameChatBridge(BotConfig config, ServerController controller) {
        this.config = config;
        this.controller = controller;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        String channelName = config.get("chat_channel", "");
        if (channelName.isBlank() || !event.getChannel().getName().equals(channelName)) {
            return;
        }
        String content = event.getMessage().getContentDisplay().strip();
        if (content.isEmpty() || content.startsWith("/")) {
            return;
        }
        if (content.length() > MAX_LENGTH) {
            content = content.substring(0, MAX_LENGTH) + "…";
        }
        String clean = content.replaceAll("[\\r\\n]+", " ");
        if (!controller.isRunning()) {
            event.getChannel().sendMessage("🛑 Server is offline — message not sent to the game.")
                    .queue();
            return;
        }
        String user = event.getAuthor().getEffectiveName().replaceAll("[\\r\\n]+", " ");
        String command = "say From discord \uD83D\uDCAC to ingame chat: " + user + ": " + clean;
        controller.sendConsole(command);
    }
}
