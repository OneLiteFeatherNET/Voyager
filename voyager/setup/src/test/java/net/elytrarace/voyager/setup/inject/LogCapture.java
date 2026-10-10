package net.elytrarace.voyager.setup.inject;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.LogEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Captures what one class logs, for the duration of one test.
 *
 * <p>A log4j2 appender is attached to a logger of its own, named after the class, and that logger is
 * removed again on {@link #close()}. Nothing is left behind for the next test, and nothing is read from
 * the console: the assertions see the events themselves, with their levels and formatted messages.
 */
public final class LogCapture implements AutoCloseable {

    private final String loggerName;
    private final LoggerContext context;
    private final Appender appender;
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    private LogCapture(Class<?> type) {
        this.loggerName = type.getName();
        this.context = (LoggerContext) LogManager.getContext(false);
        this.appender = new AbstractAppender("capture-" + loggerName, null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        LoggerConfig config = new LoggerConfig(loggerName, Level.DEBUG, false);
        config.addAppender(appender, null, null);
        Configuration configuration = context.getConfiguration();
        configuration.addLogger(loggerName, config);
        context.updateLoggers();
    }

    /** Starts capturing everything {@code type} logs. Close it, normally in a try-with-resources. */
    public static LogCapture of(Class<?> type) {
        return new LogCapture(type);
    }

    /** The formatted message of every ERROR event captured so far, in the order logged. */
    public List<String> errors() {
        return events.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(event -> event.getMessage().getFormattedMessage())
                .toList();
    }

    /** The exception attached to every ERROR event captured so far, in the order logged. */
    public List<Throwable> errorCauses() {
        return events.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(LogEvent::getThrown)
                .filter(Objects::nonNull)
                .toList();
    }

    /** The formatted message of every WARN event captured so far, in the order logged. */
    public List<String> warnings() {
        return events.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(event -> event.getMessage().getFormattedMessage())
                .toList();
    }

    @Override
    public void close() {
        context.getConfiguration().removeLogger(loggerName);
        context.updateLoggers();
        appender.stop();
    }
}
