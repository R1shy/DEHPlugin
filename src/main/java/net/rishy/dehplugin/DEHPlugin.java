package net.rishy.dehplugin;

import net.rishy.dehplugin.commands.DumpPerms;
import net.rishy.dehplugin.commands.ZeroCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DEHPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new DEHListener(this.getConfig(), this), this);
        registerCommand("dehzeromodel", new ZeroCommand());
        registerCommand("dehdumpperms", new DumpPerms());

        this.getLogger().info("[Meow Meow]");
        this.getLogger().info("DEHPlugin v" + getPluginMeta().getVersion() + " says TRANS RIGHTS");
        this.getLogger().info("[Meow Meow]");
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }
}
