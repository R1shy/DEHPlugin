package net.rishy.dehplugin.bot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns raw Minecraft console log lines into the compact format shown in the log channel. */
public final class LogFormatter {

    private static final Pattern PREFIX = Pattern.compile("^\\[\\d{1,2}:\\d{2}:\\d{2}\\] \\[[^\\]]+\\/\\w+\\]: (.*)$");
    private static final Pattern JOIN = Pattern.compile("^(.+) joined the game$");
    private static final Pattern LEAVE = Pattern.compile("^(.+) left the game$");
    private static final Pattern LOST = Pattern.compile("^(.+) lost connection: .*$");
    private static final Pattern ROLED_CHAT = Pattern.compile("^\\[(DEV|DIRECTOR|CREW)\\] ([^:]+): (.*)$");
    private static final Pattern PLAIN_CHAT = Pattern.compile("^<([^>]+)> (.*)$");
    private static final Pattern DONE = Pattern.compile("^Done \\(.*\\)! For help, type \"help\"$");
    private static final Pattern SERVER_PREFIX = Pattern.compile("^\\[Server\\] (.*)$");

    private LogFormatter() {
    }

    public static String format(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        Matcher prefix = PREFIX.matcher(rawLine);
        String message = prefix.matches() ? prefix.group(1) : rawLine.strip();

        Matcher join = JOIN.matcher(message);
        if (join.matches()) {
            return join.group(1) + " Connected";
        }
        Matcher leave = LEAVE.matcher(message);
        if (leave.matches()) {
            return leave.group(1) + " Disconnected";
        }
        Matcher lost = LOST.matcher(message);
        if (lost.matches()) {
            return lost.group(1) + " Disconnected";
        }
        Matcher roled = ROLED_CHAT.matcher(message);
        if (roled.matches()) {
            return roled.group(1) + " (" + roled.group(2).strip() + "): " + roled.group(3);
        }
        Matcher plain = PLAIN_CHAT.matcher(message);
        if (plain.matches()) {
            return plain.group(1) + ": " + plain.group(2);
        }
        if (message.equals("Stopping server")) {
            return "SERVER STATUS: OFF \uD83D\uDED1";
        }
        if (DONE.matcher(message).matches()) {
            return "SERVER STATUS: ON \u2705";
        }
        Matcher server = SERVER_PREFIX.matcher(message);
        if (server.matches()) {
            return server.group(1);
        }
        return null;
    }
}
