package net.elytrarace.voyager.api.stats;

/**
 * Stores finished races and cups. Called from the game loop, so an implementation must not block: it
 * accepts the completion and writes it elsewhere. It never throws to the caller when storage fails.
 */
public interface RaceRecorder {

    /**
     * Accepts one finished map for storage. Returns without waiting for the write.
     *
     * @param completion the map's values, one entry per human racer
     */
    void recordRace(RaceCompletion completion);

    /**
     * Accepts one finished cup for storage. Returns without waiting for the write.
     *
     * @param completion the cup's values, one entry per human racer
     */
    void recordCup(CupCompletion completion);
}
