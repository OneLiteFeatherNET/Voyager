package net.elytrarace.voyager.server.persistence;

import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.persistence.CompletionStore;
import net.elytrarace.voyager.persistence.exception.StoreException;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The write side the writer holds from the start, whether or not the database is open yet. Until a store is bound, every
 * write fails the way a lost connection fails (no SQLSTATE), so the writer keeps the completion and retries it with its
 * backoff. Once bound, the writes go to the database.
 */
final class DeferredCompletionStore implements CompletionStore {

    private final AtomicReference<CompletionStore> target = new AtomicReference<>();

    /**
     * Binds the database's store. Called once, when the database opens.
     *
     * @param store the open store
     */
    void bind(CompletionStore store) {
        target.set(store);
    }

    @Override
    public void writeRace(RaceCompletion completion) {
        current().writeRace(completion);
    }

    @Override
    public void writeCup(CupCompletion completion) {
        current().writeCup(completion);
    }

    private CompletionStore current() {
        CompletionStore store = target.get();
        if (store == null) {
            throw new StoreException("the statistics database is not open yet", null, null);
        }
        return store;
    }
}
