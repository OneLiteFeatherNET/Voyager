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
     * @param store  the write side, which writes each completion in one transaction
     * @param reader the read side, which answers {@code /stats} and {@code /leaderboard}
     */
    record Ready(CompletionStore store, StatsReader reader) implements OpenResult {
    }

    /**
     * The database could not be opened.
     *
     * @param reason one line for the operator, without any credential
     */
    record Failed(String reason) implements OpenResult {
    }
}
