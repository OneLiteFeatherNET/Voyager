package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.race.flow.RaceClock;

import java.util.List;
import java.util.UUID;

/**
 * The computer racers taking part in a cup, as the cup sees them.
 *
 * <p>The cup asks the field which racers join each map, reads where they are on each tick, and tells it
 * to let them go when the cup is over. What a competitor is, and how it is moved in the world, is the
 * field's business: the cup only ever sees an id, a position and whether the competitor is gliding.
 *
 * <p>Competitors are never part of the human players supplier. A competitor's run comes from this port
 * alone.
 */
public interface CompetitorField {

    /**
     * Readies the competitors for {@code map}, which the cup has just entered, and returns the ids of the ones that
     * race it. The cup gives each of them a fresh run at the map's start.
     *
     * @param mapIndex the zero-based position of {@code map} in the cup's rotation
     * @param map the course being entered
     * @return the ids of the competitors taking part; empty when there are none
     */
    List<UUID> prepare(int mapIndex, MapDefinition map);

    /**
     * The competitors' positions on this tick. Called once per tick, after the human racers have been advanced.
     *
     * @param clock the race clock, already advanced, so it names the tick being played
     * @return every competitor the field reports on this tick; a competitor the cup did not prepare is ignored
     */
    List<Competitor> advance(RaceClock clock);

    /** Lets the competitors go: the cup has ended or was aborted, so nothing asks for them again until the next cup. */
    void dismiss();

    /** A field with no competitors in it, which is what every cup has until one is wired in. */
    static CompetitorField none() {
        return NoCompetitors.INSTANCE;
    }
}
