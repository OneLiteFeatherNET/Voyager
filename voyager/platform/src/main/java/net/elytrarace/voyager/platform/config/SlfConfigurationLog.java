package net.elytrarace.voyager.platform.config;

import io.avaje.config.ConfigurationLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.System.Logger.Level;
import java.text.MessageFormat;

/**
 * Sends avaje-config's own messages to the server's logger. Without it avaje-config writes to the JDK logger, on stderr, in a
 * format of its own: a line at every boot, and the config check's report would carry it. Its routine messages (which files
 * were loaded) go to debug, which the server's log level hides; its warnings, such as a {@code CONFIG_FILE} that is not
 * there, stay visible at warn.
 */
final class SlfConfigurationLog implements ConfigurationLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("io.avaje.config");

    @Override
    public void log(Level level, String message, Throwable thrown) {
        switch (level) {
            case ERROR -> LOGGER.error(message, thrown);
            case WARNING -> LOGGER.warn(message, thrown);
            default -> LOGGER.debug(message, thrown);
        }
    }

    @Override
    public void log(Level level, String message, Object... args) {
        String formatted = MessageFormat.format(message, args);
        switch (level) {
            case ERROR -> LOGGER.error(formatted);
            case WARNING -> LOGGER.warn(formatted);
            default -> LOGGER.debug(formatted);
        }
    }
}
