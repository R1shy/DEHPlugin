package net.rishy.dehplugin.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import java.util.Objects;
import java.util.logging.Logger;

public class DEHUtils {
    private final String k;

    public DEHUtils(String k) {
        this.k = k;
    }

    public void sendToDiscord(String message, String botChannel, String botServer, Logger log) throws InterruptedException {
        JDA api = JDABuilder.createDefault(k).build();
        api.awaitReady();
        api.getGuildsByName(Objects.requireNonNull(botServer), false)
                .stream()
                .findFirst()
                .ifPresentOrElse(
                        guild -> guild.getTextChannelsByName(Objects.requireNonNull(botChannel), false)
                                .stream()
                                .findFirst()
                                .ifPresentOrElse(
                                        channel -> channel.sendMessage(message).queue(),
                                        () -> {}
                                ),
                        () -> log.warning(String.format("Server %s not found, is bot in it?", botServer )
                ));
        api.shutdownNow();
    }

}
