package net.rishy.dehplugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.rishy.dehplugin.commands.DumpPerms;
import net.rishy.dehplugin.commands.ZeroCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class DEHPlugin extends JavaPlugin {

    private JDA api;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // FIX: The listener and commands MUST be registered before the Discord
        // connection attempt. Previously, if the Discord bot failed to connect
        // (e.g. bot_key is "TODO" in config.yml), onEnable returned early and
        // the DEHListener was NEVER registered — meaning the door protection
        // (and chat/join prefixes) didn't exist at all, so anyone could open
        // doors. Now the plugin's core features work even if Discord is down.
        getServer().getPluginManager().registerEvents(new DEHListener(this.getConfig(), this), this);
        registerCommand("dehzeromodel", new ZeroCommand());
        registerCommand("dehdumpperms", new DumpPerms());

        String k = getConfig().getString("bot_key");
        try {
            api = JDABuilder.createDefault(k).build();
            api.awaitReady();
            sendToDiscord("Server is going up");
        } catch (Exception e) {
            this.getLogger().severe("Failed to connect to Discord: " + e.getMessage());
        }

        this.getLogger().info("[Meow Meow]");
        this.getLogger().info("DEHPlugin v" + getPluginMeta().getVersion() + " says TRANS RIGHTS");
        this.getLogger().info("[Meow Meow]");
    }

    @Override
    public void onDisable() {
        sendToDiscord("Server is going down");
        if (api != null) {
            api.shutdown();
        }
        super.onDisable();
    }

    private void sendToDiscord(String message) {
        if (api == null) {
            return;
        }
        api.getGuildsByName(Objects.requireNonNull(getConfig().getString("bot_server")), false)
                .stream()
                .findFirst()
                .ifPresentOrElse(
                        guild -> guild.getTextChannelsByName(Objects.requireNonNull(getConfig().getString("bot_channel")), false)
                                .stream()
                                .findFirst()
                                .ifPresentOrElse(
                                        channel -> channel.sendMessage(message).queue(),
                                        () -> getLogger().warning(String.format("No channel %s in %s", getConfig().getString("bot_channel"), getConfig().getString("bot_server")))
                                ),
                        () -> getLogger().warning(String.format("Server %s not found, is bot in it?", getConfig().getString("bot_server")))
                );
    }
}
