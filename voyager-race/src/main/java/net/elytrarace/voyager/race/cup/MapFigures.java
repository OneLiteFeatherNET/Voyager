package net.elytrarace.voyager.race.cup;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.MedalCountdown;
import net.elytrarace.voyager.race.scoring.MedalOutlook;

import java.time.Duration;
import java.util.List;

/**
 * The figures a racer's HUD shows during a map: the clock, the rings, the map's place in the cup, and the medal
 * outlook at the time shown. The platform draws them; the race decides them.
 *
 * <p>{@code mapNumber} is one-based, as a player reads it. The figures of a racer who has finished show the clock
 * they finished on, because their medal is decided and a clock still running would count down nothing.
 *
 * @param gameTick the current game tick of the race
 * @param elapsed the time shown
 * @param ringsPassed how many rings the racer has passed
 * @param ringCount how many rings the map has
 * @param lastRingTick the game tick of the most recently passed ring, or {@code 0} before any
 * @param mapNumber the one-based number of the map in the cup
 * @param mapCount how many maps the cup has
 * @param mapName the map's name
 * @param outlook the medal the time shown is in, and how long until it is lost
 */
public record MapFigures(int gameTick, Duration elapsed, int ringsPassed, int ringCount, int lastRingTick,
        int mapNumber, int mapCount, String mapName, MedalOutlook outlook) {

    /** The figures of a racer in flight, at {@code clock}, on {@code map}, in map {@code mapNumber} of {@code mapCount}. */
    public static MapFigures inFlight(RaceRun run, RaceClock clock, MapDefinition map, int mapNumber, int mapCount) {
        Duration elapsed = run.finishedAt().map(RaceClock::elapsed).orElse(clock.elapsed());
        List<Integer> ringTicks = run.passedOnGameTick();
        int lastRingTick = ringTicks.isEmpty() ? 0 : ringTicks.getLast();
        return new MapFigures(clock.gameTick(), elapsed, run.progress().passedCount(), map.rings().size(),
                lastRingTick, mapNumber, mapCount, map.name(), outlookAt(elapsed, map));
    }

    /** The figures shown while the start countdown runs: nothing flown yet, and the best medal on offer. */
    public static MapFigures starting(MapDefinition map, int mapNumber, int mapCount) {
        return new MapFigures(0, Duration.ZERO, 0, map.rings().size(), 0, mapNumber, mapCount, map.name(),
                outlookAt(Duration.ZERO, map));
    }

    /**
     * Against {@link MedalBrackets#DEFAULT}, the constant {@code MapScorer} classifies a finished run with, so the
     * band shown on the last tick of a race and the medal awarded cannot disagree.
     */
    private static MedalOutlook outlookAt(Duration elapsed, MapDefinition map) {
        return MedalCountdown.outlook(elapsed, map.referenceTime(), MedalBrackets.DEFAULT);
    }
}
