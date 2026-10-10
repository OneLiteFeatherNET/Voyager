package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/** Assembles a cup the way the composition root does, for tests that build one without the server's graph. */
final class CupWiring {

    private CupWiring() {
    }

    static CupSession session(CatalogHolder catalog, MapInstances instances, MapTransition transition,
            RaceRuns runs, FlightTracker tracker, RaceTimings timings, Duration step,
            Supplier<Collection<Player>> players) {
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(players, boosts), tracker, new MinestomCollisionSpace(blocks));
        return new CupSession(catalog, instances, transition, runs, flight, blocks, boosts, timings, step, players);
    }
}
