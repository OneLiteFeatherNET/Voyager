package net.elytrarace.voyager.server.cup;

import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.inject.CupBeans;
import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Assembles a cup the way the composition root does, for the cup's tests: the same collaborators, and the tick
 * order from {@link CupBeans#tickPipeline}, so a test runs the order the server runs.
 */
public final class CupWiring {

    private CupWiring() {
    }

    /** A cup and the per-tick order that drives it. */
    public record Cup(CupSession session, TickPipeline pipeline) {
    }

    public static Cup assemble(CatalogHolder catalog, MapInstances instances, MapTransition transition,
            RaceRuns runs, FlightTracker tracker, RaceTimings timings, Duration step,
            Supplier<Collection<Player>> players) {
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(players, boosts), tracker, new MinestomCollisionSpace(blocks));
        CupSession session = new CupSession(catalog, instances, transition, runs, flight, blocks, boosts, timings,
                step, players);
        return new Cup(session, new CupBeans().tickPipeline(session, boosts));
    }
}
