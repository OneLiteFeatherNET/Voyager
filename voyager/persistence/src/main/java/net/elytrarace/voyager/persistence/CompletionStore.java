package net.elytrarace.voyager.persistence;

import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.persistence.exception.StoreException;

/**
 * The write side of the database, as the writer sees it. Each method writes one completion in one transaction
 * and returns when the transaction has committed. A failed write throws {@link StoreException}.
 */
public interface CompletionStore {

    /**
     * Writes one finished map.
     *
     * @param completion the map and its human racers
     * @throws StoreException when the write fails
     */
    void writeRace(RaceCompletion completion);

    /**
     * Writes one finished cup.
     *
     * @param completion the cup and its human racers
     * @throws StoreException when the write fails
     */
    void writeCup(CupCompletion completion);
}
