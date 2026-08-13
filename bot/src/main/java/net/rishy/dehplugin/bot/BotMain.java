package net.rishy.dehplugin.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** Standalone Minecraft server bot — replaces mc-agent.py and the plugin's Discord code. */
public final class BotMain {

    private BotMain() {
    }

    public static void main(String[] args) throws Exception {
        String configPath = "bot.conf";
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--config")) {
                configPath = args[i + 1];
            }
        }

        BotConfig config = BotConfig.load(configPath);
        for (String required : new String[]{"token", "bot_server", "server_dir"}) {
            if (config.get(required) == null || config.get(required).isBlank()) {
                System.err.println("config error: '" + required + "' is required");
                System.exit(1);
            }
        }

        ServerController controller = new ServerController(config);
        System.out.println("[bot] server_dir=" + controller.serverDir() + " mode=" + controller.mode());

        JDA jda = JDABuilder.createDefault(config.get("token")).build().awaitReady();
        System.out.println("[bot] connected to Discord as " + jda.getSelfUser().getName());

        jda.getGuildsByName(config.get("bot_server"), false)
                .forEach(BotMain::registerCommands);

        DiscordCommands commands = new DiscordCommands(config, controller);
        jda.addEventListener(commands);
        jda.addEventListener(new GameChatBridge(config, controller));

        LogForwarder forwarder = new LogForwarder(jda, config, controller);
        StateWatcher watcher = new StateWatcher(jda, config, controller);
        Thread logThread = new Thread(forwarder, "log-forwarder");
        Thread stateThread = new Thread(watcher, "state-watcher");
        logThread.setDaemon(true);
        stateThread.setDaemon(true);
        logThread.start();
        stateThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            forwarder.stop();
            watcher.stop();
            commands.shutdown();
            jda.shutdown();
        }));
    }

    private static void registerCommands(Guild guild) {
        guild.upsertCommand("start", "Start the Minecraft server").queue();
        guild.upsertCommand("stop", "Gracefully stop the Minecraft server").queue();
        guild.upsertCommand("restart", "Restart the Minecraft server").queue();
        guild.upsertCommand("status", "Show server status").queue();
        guild.upsertCommand("logs", "Fetch recent server logs")
                .addOption(OptionType.INTEGER, "lines", "Number of lines (default 100, max 200)", false)
                .addOption(OptionType.BOOLEAN, "tail", "Fetch from the end (default true)", false)
                .queue();
        guild.upsertCommand("backups", "List backups on the host").queue();
        guild.upsertCommand("backup", "Create a world backup (stays on the host)")
                .addSubcommands(new SubcommandData("create", "Create a world backup"))
                .queue();
        guild.upsertCommand("files", "Manage server files")
                .addSubcommands(
                        new SubcommandData("list", "List a directory")
                                .addOption(OptionType.STRING, "path", "Directory (default root)", false),
                        new SubcommandData("read", "View a text file")
                                .addOption(OptionType.STRING, "path", "File to view", true)
                                .addOption(OptionType.INTEGER, "lines", "Max lines (max 200)", false)
                                .addOption(OptionType.BOOLEAN, "tail", "End vs start (default true)", false),
                        new SubcommandData("download", "Download a file (up to 25 MB)")
                                .addOption(OptionType.STRING, "path", "File to download", true),
                        new SubcommandData("write", "Write text to a file (admins)")
                                .addOption(OptionType.STRING, "path", "File", true)
                                .addOption(OptionType.STRING, "content", "Text content", false)
                                .addOption(OptionType.ATTACHMENT, "file", "Upload an attachment instead", false),
                        new SubcommandData("upload", "Upload a file (admins)")
                                .addOption(OptionType.STRING, "path", "Destination path", true)
                                .addOption(OptionType.ATTACHMENT, "file", "File to upload", true),
                        new SubcommandData("mkdir", "Create a directory (admins)")
                                .addOption(OptionType.STRING, "path", "Directory to create", true),
                        new SubcommandData("move", "Move or rename (admins)")
                                .addOption(OptionType.STRING, "path", "Source path", true)
                                .addOption(OptionType.STRING, "to", "Destination path", true),
                        new SubcommandData("delete", "Delete files or directories (admins)")
                                .addOption(OptionType.STRING, "paths", "Comma-separated paths", true),
                        new SubcommandData("extract", "Extract a zip (admins)")
                                .addOption(OptionType.STRING, "path", "Zip file", true)
                                .addOption(OptionType.STRING, "to", "Destination directory", true))
                .queue();
    }
}
