package net.elytrarace.voyager.server.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.game.CupSession;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * The race's own composition: the cup being played, which is the one bean that ties the race's
 * services together.
 *
 * <p>The cup session is the top of the graph. It is the only bean that depends on everything the
 * race needs, and it is what {@code VoyagerServer} and the tick loop hold.
 */
@Factory
public final class RaceBeans {

    /**
     * {@link MinecraftServer#TICK_MS} is what the server actually ticks at, so the phase driver's idea of
     * how much time a tick stands for and the server's cannot drift apart.
     */
    @Bean
    CupSession cupSession(CatalogHolder catalog, MapInstances instances,
            MapTransition transition, RaceRuns runs, FlightTracker tracker, RaceTimings timings,
            Supplier<Collection<Player>> players) {
        return CupSession.create(catalog, instances, transition, runs, tracker, timings,
                Duration.ofMillis(MinecraftServer.TICK_MS), players);
    }
}
