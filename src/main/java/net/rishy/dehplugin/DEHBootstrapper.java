package net.rishy.dehplugin;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import net.rishy.dehplugin.commands.DEHDiscordAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.LoggerContext;
import org.jetbrains.annotations.NotNull;

public class DEHBootstrapper implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        Configuration config = loggerContext.getConfiguration();

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("[%d{yyyy-MM-dd HH:mm:ss} %level]: %msg%n")
                .withConfiguration(config)
                .build();
        DEHDiscordAppender appender = DEHDiscordAppender.createAppender("MyPaperAppender", null, layout);
        appender.start();

        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, null, null);

        loggerContext.updateLoggers();
    }
}
