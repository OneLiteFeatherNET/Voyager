package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.config.DatabaseConfig;
import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.HibernateException;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Opens the statistics store for one server start. The composition root calls this once at boot, and calls it again
 * after a failure when it wants to try the database later.
 *
 * <p>The factory never throws for a database that cannot be used: an unreachable server, a failed migration or a schema
 * that does not match the mappings all give {@link StatsStoreOutcome.Unavailable}, with one warning through the log
 * that names the stage and never contains the password (spec "A database that cannot be used at boot does not stop the
 * game").
 */
public final class StatsStoreFactory {

    private StatsStoreFactory() {
    }

    /**
     * Opens the database of an enabled configuration: the pool, the migrations, then the session factory, in that
     * order (design D9).
     *
     * @param config        the database settings; must be enabled
     * @param leakDetection {@code true} in development, {@code false} in production
     * @param log           where the one warning goes when the database cannot be opened
     * @return the open store, or the reason it is not open
     * @throws IllegalArgumentException when {@code config} is not enabled; a disabled configuration binds no store
     */
    public static StatsStoreOutcome open(DatabaseConfig config, boolean leakDetection, StatsLog log) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(log, "log must not be null");
        if (!config.enabled()) {
            throw new IllegalArgumentException("only an enabled database configuration can be opened");
        }
        try {
            StatsDatabase database = StatsDatabase.open(
                    config.url(), config.user(), config.password().value(), config.poolSize(), leakDetection);
            return new StatsStoreOutcome.Available(new PostgresCompletionStore(database.sessionFactory()), database);
        } catch (RuntimeException failure) {
            String reason = reason(failure);
            log.warn("statistics stay off for this start: %s; the game runs without them".formatted(reason));
            return new StatsStoreOutcome.Unavailable(reason, failure);
        }
    }

    /** The stage that failed, the root exception class and the SQLSTATE when there is one. No message is copied. */
    private static String reason(RuntimeException failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String sqlState = sqlState(failure);
        String stage;
        if (isConnectionFailure(failure)) {
            stage = "the database cannot be reached";
        } else if (failure instanceof HibernateException) {
            stage = "the schema does not match the mappings";
        } else if (failure instanceof FlywayException) {
            stage = "the schema migration failed";
        } else {
            stage = "the statistics database could not be opened";
        }
        return sqlState == null
                ? "%s (%s)".formatted(stage, root.getClass().getSimpleName())
                : "%s (%s, SQLSTATE %s)".formatted(stage, root.getClass().getSimpleName(), sqlState);
    }

    private static boolean isConnectionFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && (sqlException.getSQLState() == null || sqlException.getSQLState().startsWith("08"))) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    @Nullable
    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }
}
