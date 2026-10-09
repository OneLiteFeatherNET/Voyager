package net.elytrarace.voyager.server.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogConsistency;
import net.elytrarace.voyager.platform.catalog.JsonCupCatalog;
import net.elytrarace.voyager.platform.catalog.JsonMapCatalog;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.game.CupResolution;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceManager;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * The services of the server that are not the race's own: Minestom's instance manager, the two
 * catalogues and the ports they answer, the cup with its consistency check, the world handles, and
 * the live roster.
 *
 * <p>This is the half of the graph that serves the server rather than the race. Every bean is a
 * singleton, which is avaje's default for a {@code @Bean}. The {@code MapCatalog} and {@code CupCatalog}
 * ports have no bean of their own; the catalogue beans already serve them (see below).
 *
 * <h2>Constructors are called here, not annotated</h2>
 *
 * <p>No class in {@code voyager-platform} carries an annotation. Each platform class is constructed by
 * the {@code @Bean} method that needs it, so the wiring is written out where a reader can see it and a
 * change to a constructor is a compile error here rather than a runtime surprise.
 *
 * <h2>Two orderings this factory depends on</h2>
 *
 * <p>{@code MinecraftServer.init()} must already have run: {@link #instanceManager()} reaches for
 * {@code MinecraftServer.getInstanceManager()}, and the registries behind it do not exist before
 * {@code init}. {@code VoyagerServer.main} calls {@code init} before it opens the graph, and that is the
 * only correct order.
 *
 * <p>{@link #cup} runs the cross-catalogue consistency check before it hands a cup back, so a cup
 * naming a map nothing provides fails while the graph is being built rather than the first time that
 * map comes up in the rotation, minutes after anybody was watching.
 */
@Factory
public final class ServerBeans {

    @Bean
    InstanceManager instanceManager() {
        return MinecraftServer.getInstanceManager();
    }

    @Bean
    JsonMapCatalog jsonMapCatalog(@External ServerSettings settings) {
        return new JsonMapCatalog(settings.dataPath().resolve("maps"));
    }

    @Bean
    JsonCupCatalog jsonCupCatalog(@External ServerSettings settings) {
        return new JsonCupCatalog(settings.dataPath().resolve("cups"));
    }

    // No bean for the MapCatalog and CupCatalog ports. avaje registers a bean under every interface
    // its class implements, so the two JSON catalogue beans already answer a MapCatalog or CupCatalog
    // request with the same singleton. A @Bean that returned the same instance again would register it
    // a second time, and every JsonMapCatalog or JsonCupCatalog request would then be ambiguous.
    // Everything that plays a race is handed a port; only the cup resolution and the consistency
    // check take the concrete type, because only they have to enumerate.

    @Bean
    CupDefinition cup(JsonCupCatalog cups, JsonMapCatalog maps, @External ServerSettings settings) {
        CatalogConsistency.requireEveryCupMapResolves(cups, maps);
        return CupResolution.resolve(cups, settings.cupName());
    }

    @Bean
    MapInstances mapInstances(InstanceManager instances, @External ServerSettings settings) {
        return new MapInstances(instances, settings.worldsPath());
    }

    @Bean
    RaceRuns raceRuns() {
        return new RaceRuns();
    }

    @Bean
    MapTransition mapTransition(MapInstances instances, RaceRuns runs) {
        return new MapTransition(instances, runs);
    }

    @Bean
    FlightTracker flightTracker() {
        return new FlightTracker();
    }

    @Bean
    RaceTimings raceTimings(@External ServerSettings settings) {
        return settings.timings();
    }

    /**
     * Everyone online, re-read on every call rather than captured once: the roster changes under the
     * tick loop, and a snapshot taken at wiring time is a roster that is empty forever.
     */
    @Bean
    Supplier<Collection<Player>> onlinePlayers() {
        return () -> MinecraftServer.getConnectionManager().getOnlinePlayers();
    }
}
