package net.elytrarace.voyager.persistence;

import org.slf4j.Logger;

import java.util.Objects;

/** The writer's log lines through the slf4j facade the rest of the server logs with. */
public final class Slf4jStatsLog implements StatsLog {

    private final Logger logger;

    /**
     * @param logger the logger the lines are written to
     */
    public Slf4jStatsLog(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }
}
