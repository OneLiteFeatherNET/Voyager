package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.race.scoring.MedalOutlook;

import java.time.Duration;

/**
 * Everything the live surfaces show, as one value computed by whoever is running the race.
 *
 * <h2>The race computes; the platform renders</h2>
 *
 * <p>This record is the seam. A tick produces one of these from the run, the clock and the map; the
 * HUD turns it into an action bar and a boss bar and knows nothing about racing. The alternative —
 * a HUD component that reaches back into a run to ask what to draw — is the shape the greenfield
 * design names as its counter-example, and the renderer split that the tree being replaced wrote
 * down and never did.
 *
 * @param gameTick the race clock's tick, which is what the refresh cadence and the ring flash are
 *     counted in. Zero before the race starts, which is what the start countdown shows a bar with.
 * @param elapsed the race time {@code gameTick} stands for
 * @param ringsPassed how many rings this racer has passed
 * @param ringCount how many the map has
 * @param lastRingGameTick the tick the most recent ring was passed on, or {@code 0} for none — game
 *     ticks are one-based, so zero is not a tick anything happened on. {@link RaceHud} reads it to
 *     decide whether the counter is still flashing, which is why no renderer has to remember it.
 * @param mapNumber which map of the cup this is, one-based, as a player counts
 * @param mapCount how many maps the cup plays
 * @param mapName the map's name
 * @param outlook the best medal still reachable and how long is left to keep it
 */
public record HudState(int gameTick, Duration elapsed, int ringsPassed, int ringCount,
        int lastRingGameTick, int mapNumber, int mapCount, String mapName, MedalOutlook outlook) {

    public HudState {
        if (gameTick < 0) {
            throw new IllegalArgumentException("gameTick must not be negative, was %d".formatted(gameTick));
        }
        if (ringCount < 1) {
            throw new IllegalArgumentException(
                    "a map with no rings is not a course, was %d".formatted(ringCount));
        }
        if (ringsPassed < 0 || ringsPassed > ringCount) {
            throw new IllegalArgumentException(
                    "rings passed must be within 0..%d, was %d".formatted(ringCount, ringsPassed));
        }
        if (lastRingGameTick < 0 || lastRingGameTick > gameTick) {
            throw new IllegalArgumentException(
                    "the last ring was passed on a tick that has not been played: %d of %d"
                            .formatted(lastRingGameTick, gameTick));
        }
        if (mapCount < 1) {
            throw new IllegalArgumentException("a cup plays at least one map, was %d".formatted(mapCount));
        }
        if (mapNumber < 1 || mapNumber > mapCount) {
            throw new IllegalArgumentException(
                    "map number must be within 1..%d, was %d".formatted(mapCount, mapNumber));
        }
    }
}
