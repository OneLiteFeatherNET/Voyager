package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.persistence.schema.StatsDatabase;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * What a server start gets from {@link StatsStoreFactory#open}: an open store, or the reason there is none. A start
 * never fails because of the database (design D5, owner decision Q1), so the outcome is a value and not an exception.
 */
public sealed interface StatsStoreOutcome permits StatsStoreOutcome.Available, StatsStoreOutcome.Unavailable {

    /**
     * The database is open and its schema matches the mappings. The store writes completions to it, and the database
     * is the handle the reader and the shutdown use: the reader takes the session factory, and the shutdown closes
     * the database after the writer has drained.
     *
     * @param store    the completion store over the database's session factory
     * @param database the open database; its pool is closed by {@link StatsDatabase#close()}
     */
    record Available(CompletionStore store, StatsDatabase database) implements StatsStoreOutcome {

        public Available {
            Objects.requireNonNull(store, "store must not be null");
            Objects.requireNonNull(database, "database must not be null");
        }
    }

    /**
     * The database could not be opened for this start. The reason names the stage that failed and never contains a
     * credential; {@code cause} keeps the failure for a caller that wants the detail, and is never printed.
     *
     * @param reason the one-line reason for the operator
     * @param cause  the failure, or {@code null} when there is none to keep
     */
    record Unavailable(String reason, @Nullable Throwable cause) implements StatsStoreOutcome {

        public Unavailable {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
