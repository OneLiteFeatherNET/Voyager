package net.elytrarace.voyager.server.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.cup.TickStep;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * The cup's own composition: the cup session and the collaborators only it uses, wired together.
 *
 * <p>The cup session is the top of this graph. The flight driver samples the players through the boost tracker
 * and reads blocks through the block source, so those beans are declared here, next to the session that needs
 * them. Every bean is a constructor call; the cup itself computes nothing here.
 */
@Factory
public final class CupBeans {

    @Bean
    CurrentMapBlocks currentMapBlocks() {
        return new CurrentMapBlocks();
    }

    @Bean
    FireworkBoostTracker fireworkBoostTracker() {
        return new FireworkBoostTracker();
    }

    @Bean
    LivePlayerSampler livePlayerSampler(Supplier<Collection<Player>> players, FireworkBoostTracker boosts) {
        return new LivePlayerSampler(players, boosts);
    }

    @Bean
    MinestomCollisionSpace minestomCollisionSpace(CurrentMapBlocks blocks) {
        return new MinestomCollisionSpace(blocks);
    }

    @Bean
    FlightTickDriver flightTickDriver(LivePlayerSampler sampler, FlightTracker tracker,
            MinestomCollisionSpace collision) {
        return new FlightTickDriver(sampler, tracker, collision);
    }

    /**
     * {@link MinecraftServer#TICK_MS} is what the server actually ticks at, so the phase driver's idea of
     * how much time a tick stands for and the server's cannot drift apart.
     */
    /**
     * The cup's per-tick order, declared once, here. A tick samples the flight first, so that anything later in the
     * tick reads this tick's answer; it counts the boosts down after that sample, so a burn of N ticks drives N of
     * them; and it advances the phase last, so a phase that ends announces its result on the same tick.
     *
     * <p>Public so that the cup's tests run the same order, rather than a copy of it.
     */
    @Bean
    public TickPipeline tickPipeline(CupSession session, FireworkBoostTracker boosts) {
        return TickPipeline.of(List.of(
                new TickStep("flight sample", session::sampleFlight),
                new TickStep("boost burn", boosts::advance),
                new TickStep("phase advance", session::advancePhase)));
    }

    @Bean
    CupSession cupSession(CatalogHolder catalog, MapInstances instances, MapTransition transition, RaceRuns runs,
            FlightTickDriver flight, CurrentMapBlocks blocks, FireworkBoostTracker boosts, RaceTimings timings,
            Supplier<Collection<Player>> players) {
        return new CupSession(catalog, instances, transition, runs, flight, blocks, boosts, timings,
                Duration.ofMillis(MinecraftServer.TICK_MS), players);
    }
}
