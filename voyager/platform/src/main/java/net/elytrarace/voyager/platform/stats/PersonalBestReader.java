package net.elytrarace.voyager.platform.stats;

import net.elytrarace.voyager.api.race.GameMode;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.UUID;

/**
 * Reads a racer's stored best time on one map, for the personal best clause of a place line.
 *
 * <p>The read is started at the map's GO and answered off the game loop. The cup never waits for it: a finish that comes
 * before the answer is told the place and nothing about a best.
 */
@NotNullByDefault
public interface PersonalBestReader {

    /**
     * Whether statistics are kept on this server. When they are not, no read is started and no clause is shown.
     *
     * @return {@code true} when a stored best can exist to be read
     */
    boolean enabled();

    /**
     * Starts the read of one racer's best on one map, layout and mode.
     *
     * @param playerId the racer's id
     * @param mapKey the map's name in its file
     * @param layoutVersion the layout the map is played on
     * @param mode the mode the cup is played in
     * @return the answer, filled off the game loop; failed when the read cannot be made
     */
    BestAnswer bestOn(UUID playerId, String mapKey, String layoutVersion, GameMode mode);
}
