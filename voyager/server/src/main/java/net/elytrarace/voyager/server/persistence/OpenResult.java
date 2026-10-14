package net.elytrarace.voyager.server.persistence;

import net.elytrarace.voyager.api.stats.StatsReader;
import net.elytrarace.voyager.persistence.CompletionStore;

/**
 * What one attempt to open the statistics database gives back: the write side and the read side, or the reason there is
 * none. The reason names the stage that failed and never carries a credential.
 */
public sealed interface OpenResult permits OpenResult.Ready, OpenResult.Failed {

    /**
     * The database is open.
     *
     * @param store     the write side, which writes each completion in one transaction
     * @param reader    the read side, which answers {@code /stats} and {@code /leaderboard}
     * @param closePool closes the connection pool; called once, at shutdown, after the writer has stopped
     */
    record Ready(CompletionStore store, StatsReader reader, Runnable closePool) implements OpenResult {

        /** An open database whose pool needs no closing, for a caller that owns none. */
        public Ready(CompletionStore store, StatsReader reader) {
            this(store, reader, () -> {
            });
        }
    }

    /**
     * The database could not be opened.
     *
     * @param reason one line for the operator, without any credential
     */
    record Failed(String reason) implements OpenResult {
    }
}
