package net.rishy.dehplugin.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
@Plugin(name = "MyCustomAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class DEHDiscordAppender extends AbstractAppender {





        private static volatile String token;
        private static volatile String channelName;
        private static volatile String serverName;
        private static volatile JDA jda;

        protected DEHDiscordAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
            super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
        }

        public static void init(String token, String channelName, String serverName) {
            DEHDiscordAppender.token = token;
            DEHDiscordAppender.channelName = channelName;
            DEHDiscordAppender.serverName = serverName;
            if (token == null) {
                return;
            }
            new Thread(() -> {
                try {
                    jda = JDABuilder.createDefault(token).build().awaitReady();
                } catch (Exception ignored) {
                }
            }).start();
        }

        public static void shutdown() {
            JDA api = jda;
            jda = null;
            if (api != null) {
                api.shutdownNow();
            }
        }

        @Override
        public void append(LogEvent event) {
            JDA api = jda;
            if (api == null || channelName == null || serverName == null) {
                return;
            }
            try {
                byte[] formattedBytes = getLayout().toByteArray(event);
                String message = new String(formattedBytes);
                String trimmed = message.trim();
                if (trimmed.contains("Thread RCON Client")
                        && (trimmed.endsWith("started") || trimmed.endsWith("shutting down"))) {
                    return;
                }
                api.getGuildsByName(serverName, false).stream()
                        .findFirst()
                        .flatMap(guild -> guild.getTextChannelsByName(channelName, false).stream().findFirst())
                        .ifPresent(channel -> channel.sendMessage(message).queue());
            } catch (Throwable ignored) {
            }
        }

        @PluginFactory
        public static DEHDiscordAppender createAppender(
                @PluginAttribute("name") String name,
                @PluginElement("Filter") Filter filter,
                @PluginElement("Layout") Layout<? extends Serializable> layout) {
            return new DEHDiscordAppender( name, filter, layout, true);
        }
    }

