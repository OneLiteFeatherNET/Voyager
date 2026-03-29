package net.elytrarace.server.cup;

import java.util.Optional;

/**
 * Manages the progression through maps within a cup. Tracks which map is currently
 * active and provides methods to advance through the cup sequence.
 */
public interface CupFlowService {

    /**
     * Starts a cup, resetting any previous state and loading the first map.
     *
     * @param cup the cup definition to start
     * @throws IllegalArgumentException if the cup contains no maps
     */
    void startCup(CupDefinition cup);

    /**
     * Returns the currently active map, or empty if no cup has been started.
     *
     * @return the current map definition
     */
    Optional<MapDefinition> getCurrentMap();

    /**
     * Checks whether there is another map after the current one.
     *
     * @return {@code true} if there is a next map, {@code false} otherwise
     */
    boolean hasNextMap();

    /**
     * Advances to the next map in the cup sequence.
     *
     * @throws IllegalStateException if the cup is already complete or no cup has been started
     */
    void advanceToNextMap();

    /**
     * Checks whether all maps in the cup have been played.
     *
     * @return {@code true} if the current map is the last one and cannot advance further
     */
    boolean isCupComplete();

    /**
     * Returns the zero-based index of the currently active map.
     *
     * @return the current map index
     * @throws IllegalStateException if no cup has been started
     */
    int getCurrentMapIndex();

    /**
     * Returns the total number of maps in the current cup.
     *
     * @return the total map count
     * @throws IllegalStateException if no cup has been started
     */
    int getTotalMaps();
}
