package net.rishy.dehplugin.bot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Slash command dispatch with two permission tiers (admins vs readers). */
public final class DiscordCommands extends ListenerAdapter {

    private static final int MESSAGE_LIMIT = 1990;
    private static final Set<String> READER_COMMANDS = Set.of("status", "logs", "backups");

    private final BotConfig config;
    private final ServerController controller;
    private final FilesApi filesApi;
    private final BackupsApi backupsApi;
    private final Set<String> admins;
    private final Set<String> readers;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "discord-worker");
        thread.setDaemon(true);
        return thread;
    });

    public DiscordCommands(BotConfig config, ServerController controller) {
        this.config = config;
        this.controller = controller;
        this.filesApi = new FilesApi(config);
        this.backupsApi = new BackupsApi(config, controller.serverDir());
        this.admins = Set.copyOf(config.getList("admins"));
        this.readers = Set.copyOf(config.getList("readers"));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!allowed(event)) {
            event.reply("You don't have permission to use that command.").setEphemeral(true).queue();
            return;
        }
        switch (event.getName()) {
            case "start" -> power(event, "start");
            case "stop" -> power(event, "stop");
            case "restart" -> power(event, "restart");
            case "status" -> status(event);
            case "logs" -> logs(event);
            case "files" -> files(event);
            case "backup" -> backup(event);
            case "backups" -> backupsList(event);
        }
    }

    private boolean allowed(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        if (admins.contains(userId)) {
            return true;
        }
        if (!readers.contains(userId)) {
            return false;
        }
        String name = event.getName();
        String sub = event.getSubcommandName();
        if (READER_COMMANDS.contains(name)) {
            return true;
        }
        return name.equals("files") && sub != null && Set.of("list", "read", "download").contains(sub);
    }

    private void power(SlashCommandInteractionEvent event, String action) {
        event.deferReply().queue();
        executor.submit(() -> {
            Result result = switch (action) {
                case "start" -> controller.start();
                case "stop" -> controller.stop();
                default -> controller.restart();
            };
            reply(event, (result.ok() ? "✅ " : "❌ ") + result.detail());
        });
    }

    private void status(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        executor.submit(() -> {
            Map<String, Object> info = controller.state();
            StringBuilder sb = new StringBuilder("**Server status**\n");
            info.forEach((key, value) -> sb.append('`').append(key).append("`: ")
                    .append(formatValue(key, value)).append('\n'));
            reply(event, sb.toString().strip());
        });
    }

    private void logs(SlashCommandInteractionEvent event) {
        int lines = intOption(event, "lines", 100);
        boolean tail = boolOption(event, "tail", true);
        event.deferReply().queue();
        executor.submit(() -> {
            try {
                List<String> fetched = tail
                        ? LogTail.tail(controller.serverDir(), Math.min(lines, 200))
                        : LogTail.head(controller.serverDir(), Math.min(lines, 200));
                if (fetched.isEmpty()) {
                    reply(event, "No log lines found.");
                } else {
                    reply(event, "```\n" + truncate(String.join("\n", fetched)) + "\n```");
                }
            } catch (Exception e) {
                reply(event, "❌ " + e.getMessage());
            }
        });
    }

    private void files(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        String path = stringOption(event, "path", "");
        switch (sub == null ? "" : sub) {
            case "list" -> {
                event.deferReply().queue();
                executor.submit(() -> {
                    try {
                        reply(event, filesApi.list(controller.serverDir(), path));
                    } catch (Exception e) {
                        reply(event, "❌ " + e.getMessage());
                    }
                });
            }
            case "read" -> readFile(event, path);
            case "download" -> downloadFile(event, path);
            case "write" -> writeFile(event, path);
            case "upload" -> uploadFile(event, path);
            case "mkdir" -> {
                event.deferReply().queue();
                executor.submit(() -> {
                    try {
                        reply(event, "✅ " + filesApi.mkdir(controller.serverDir(), path).detail());
                    } catch (Exception e) {
                        reply(event, "❌ " + e.getMessage());
                    }
                });
            }
            case "move" -> {
                String to = stringOption(event, "to", "");
                event.deferReply().queue();
                executor.submit(() -> {
                    try {
                        reply(event, "✅ " + filesApi.move(controller.serverDir(), path, to).detail());
                    } catch (Exception e) {
                        reply(event, "❌ " + e.getMessage());
                    }
                });
            }
            case "delete" -> {
                List<String> paths = java.util.Arrays.stream(path.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                event.deferReply().queue();
                executor.submit(() -> {
                    try {
                        reply(event, filesApi.delete(controller.serverDir(), paths).detail());
                    } catch (Exception e) {
                        reply(event, "❌ " + e.getMessage());
                    }
                });
            }
            case "extract" -> {
                String to = stringOption(event, "to", "");
                event.deferReply().queue();
                executor.submit(() -> {
                    try {
                        reply(event, "✅ " + filesApi.extract(controller.serverDir(), path, to).detail());
                    } catch (Exception e) {
                        reply(event, "❌ " + e.getMessage());
                    }
                });
            }
            default -> reply(event, "Unknown files subcommand.");
        }
    }

    private void readFile(SlashCommandInteractionEvent event, String path) {
        int lines = intOption(event, "lines", 0);
        boolean tail = boolOption(event, "tail", true);
        event.deferReply().queue();
        executor.submit(() -> {
            try {
                FilesApi.FileRead file = filesApi.read(controller.serverDir(), path);
                List<String> all = List.of(file.content().split("\n"));
                List<String> shown = all;
                if (lines > 0) {
                    int n = Math.min(lines, 200);
                    shown = tail ? all.subList(Math.max(all.size() - n, 0), all.size())
                            : all.subList(0, Math.min(n, all.size()));
                }
                if (shown.isEmpty()) {
                    reply(event, "`" + path + "` is empty.");
                } else {
                    reply(event, "`" + path + "` (" + file.size() + " bytes)\n```\n"
                            + truncate(String.join("\n", shown)) + "\n```");
                }
            } catch (Exception e) {
                reply(event, "❌ " + e.getMessage());
            }
        });
    }

    private void downloadFile(SlashCommandInteractionEvent event, String path) {
        event.deferReply().queue();
        executor.submit(() -> {
            try {
                byte[] bytes = filesApi.download(controller.serverDir(), path);
                String name = Path.of(path).getFileName().toString();
                event.getHook().sendFiles(FileUpload.fromData(bytes, name)).queue();
            } catch (Exception e) {
                reply(event, "❌ " + e.getMessage());
            }
        });
    }

    private void writeFile(SlashCommandInteractionEvent event, String path) {
        String content = stringOption(event, "content", "");
        var attachment = event.getOption("file");
        event.deferReply().queue();
        executor.submit(() -> {
            try {
                if (attachment != null && attachment.getAsAttachment() != null) {
                    InputStream in = attachment.getAsAttachment().getProxy().download()
                            .get(60, TimeUnit.SECONDS);
                    reply(event, "✅ " + filesApi.upload(controller.serverDir(), path,
                            in.readAllBytes(), true).detail());
                } else if (!content.isEmpty()) {
                    reply(event, "✅ " + filesApi.write(controller.serverDir(), path,
                            content, true).detail());
                } else {
                    reply(event, "❌ Provide either text content or a file attachment.");
                }
            } catch (Exception e) {
                reply(event, "❌ " + e.getMessage());
            }
        });
    }

    private void uploadFile(SlashCommandInteractionEvent event, String path) {
        var attachment = event.getOption("file");
        event.deferReply().queue();
        executor.submit(() -> {
            try {
                if (attachment == null || attachment.getAsAttachment() == null) {
                    reply(event, "❌ Attach a file to upload.");
                    return;
                }
                InputStream in = attachment.getAsAttachment().getProxy().download()
                        .get(60, TimeUnit.SECONDS);
                reply(event, "✅ " + filesApi.upload(controller.serverDir(), path,
                        in.readAllBytes(), true).detail());
            } catch (Exception e) {
                reply(event, "❌ " + e.getMessage());
            }
        });
    }

    private void backup(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (!"create".equals(sub)) {
            reply(event, "Unknown backup subcommand.");
            return;
        }
        event.deferReply().queue();
        executor.submit(() -> {
            try {
                BackupsApi.BackupResult result = backupsApi.create(controller.serverDir());
                StringBuilder sb = new StringBuilder("✅ Backup created: `").append(result.archive())
                        .append("` (").append(FilesApi.humanSize(result.size())).append(")\n")
                        .append("Worlds: ").append(String.join(", ", result.worlds()));
                if (!result.pruned().isEmpty()) {
                    sb.append("\nPruned: ").append(String.join(", ", result.pruned()));
                }
                sb.append("\nThe archive stays on the host under `").append(backupsApi.backupDir())
                        .append("` — it is not sent to Discord.");
                reply(event, sb.toString());
            } catch (Exception e) {
                reply(event, "❌ " + e.getMessage());
            }
        });
    }

    private void backupsList(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        executor.submit(() -> {
            try {
                List<BackupsApi.BackupInfo> backups = backupsApi.list();
                if (backups.isEmpty()) {
                    reply(event, "No backups yet.");
                    return;
                }
                StringBuilder sb = new StringBuilder("**Backups** (`").append(backupsApi.backupDir()).append("`)\n");
                for (BackupsApi.BackupInfo backup : backups) {
                    sb.append('`').append(backup.name()).append("`  ")
                            .append(FilesApi.humanSize(backup.size())).append("  ")
                            .append(BackupsApi.formatTime(backup.modified())).append('\n');
                }
                reply(event, sb.toString().strip());
            } catch (Exception e) {
                reply(event, "❌ " + e.getMessage());
            }
        });
    }

    private void reply(SlashCommandInteractionEvent event, String text) {
        event.getHook().editOriginal(truncate(text)).queue();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "no output";
        }
        String stripped = text.strip();
        if (stripped.length() <= MESSAGE_LIMIT) {
            return stripped;
        }
        return stripped.substring(0, MESSAGE_LIMIT) + "\n…(truncated)";
    }

    private static String formatValue(String key, Object value) {
        if (value instanceof Boolean b) {
            return b ? "🟢 up" : "🛑 down";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            map.forEach((k, v) -> sb.append(k).append(": ").append(v).append(", "));
            String result = sb.toString();
            return result.isEmpty() ? "none" : result.substring(0, result.length() - 2);
        }
        if (key.startsWith("disk_") && value instanceof Long l) {
            return FilesApi.humanSize(l);
        }
        if (key.equals("log_age") && value instanceof Long l) {
            return formatAge(l);
        }
        return String.valueOf(value);
    }

    private static String formatAge(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m ago";
        }
        return (seconds / 3600) + "h ago";
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name, String def) {
        var mapping = event.getOption(name);
        return mapping == null ? def : mapping.getAsString();
    }

    private static int intOption(SlashCommandInteractionEvent event, String name, int def) {
        var mapping = event.getOption(name);
        return mapping == null ? def : (int) mapping.getAsLong();
    }

    private static boolean boolOption(SlashCommandInteractionEvent event, String name, boolean def) {
        var mapping = event.getOption(name);
        return mapping == null ? def : mapping.getAsBoolean();
    }
}
