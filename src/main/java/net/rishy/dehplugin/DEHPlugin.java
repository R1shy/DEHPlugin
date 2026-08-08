package net.rishy.dehplugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.rishy.dehplugin.commands.DEHDiscordAppender;
import net.rishy.dehplugin.commands.DEHUtils;
import net.rishy.dehplugin.commands.DumpPerms;
import net.rishy.dehplugin.commands.ZeroCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class DEHPlugin extends JavaPlugin {

    private DEHUtils utils;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new DEHListener(this.getConfig(), this), this);
        registerCommand("dehzeromodel", new ZeroCommand());
        registerCommand("dehdumpperms", new DumpPerms());

        String k = getConfig().getString("bot_key");
        this.utils = new DEHUtils(k);
        DEHDiscordAppender.init(k, getConfig().getString("log_channel"), getConfig().getString("bot_server"));

        try {
            utils.sendToDiscord("Server is going down", getConfig().getString("bot_server"), getConfig().getString("bot_channel"), getLogger());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.getLogger().info("[Meow Meow]");
        this.getLogger().info("DEHPlugin v" + getPluginMeta().getVersion() + " says TRANS RIGHTS");
        this.getLogger().info("[Meow Meow]");
    }

    @Override
    public void onDisable() {
        DEHDiscordAppender.shutdown();
        try {

            utils.sendToDiscord("Server is going down", getConfig().getString("bot_server"), getConfig().getString("bot_channel"), getLogger());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        super.onDisable();
    }


}
