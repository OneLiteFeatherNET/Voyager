package net.elytrarace.voyager.server.inject;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import net.elytrarace.voyager.api.race.CupCatalog;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapCatalog;
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
import net.elytrarace.voyager.server.game.CupSession;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceManager;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * The object graph. Every DI annotation in the rebuild is in this file and in the one that reads it;
 * {@code ApiPurityTest.onlyServerDependsOnDiContainer} is what keeps that true.
 *
 * <h2>Constructors and {@code @Provides}, no field or annotation injection</h2>
 *
 * <p>Nothing outside this package carries an {@code @Inject}. The domain and platform types are
 * ordinary objects with ordinary constructors — that is the whole point of confining the container
 * to a composition root — so the wiring has to be written out here. It is longer than annotating the
 * constructors would be, and it is the version where a reader can see the graph.
 *
 * <h2>Two orderings this module depends on</h2>
 *
 * <p>{@code MinecraftServer.init()} must already have run: {@link #instanceManager()} reaches for
 * {@code MinecraftServer.getInstanceManager()}, and the registries behind it do not exist before
 * {@code init}. {@code VoyagerServer.main} calls {@code init} before it builds the injector, and
 * that is the only correct order.
 *
 * <p>{@link #cup} runs the cross-catalogue consistency check before it hands a cup back, so a cup
 * naming a map nothing provides fails while the graph is being built rather than the first time that
 * map comes up in the rotation — which, in a rotation, is minutes after anybody was watching.
 */
public final class VoyagerModule extends AbstractModule {

    private final ServerSettings settings;

    public VoyagerModule(ServerSettings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        bind(ServerSettings.class).toInstance(settings);
    }

    @Provides
    @Singleton
    InstanceManager instanceManager() {
        return MinecraftServer.getInstanceManager();
    }

    @Provides
    @Singleton
    JsonMapCatalog jsonMapCatalog(ServerSettings settings) {
        return new JsonMapCatalog(settings.dataPath().resolve("maps"));
    }

    @Provides
    @Singleton
    JsonCupCatalog jsonCupCatalog(ServerSettings settings) {
        return new JsonCupCatalog(settings.dataPath().resolve("cups"));
    }

    // The ports, bound to the concrete catalogues. Everything that plays a race is handed a port;
    // only the cup resolution and the consistency check need the concrete type, because only they
    // have to enumerate.
    @Provides
    @Singleton
    MapCatalog mapCatalog(JsonMapCatalog maps) {
        return maps;
    }

    @Provides
    @Singleton
    CupCatalog cupCatalog(JsonCupCatalog cups) {
        return cups;
    }

    @Provides
    @Singleton
    CupDefinition cup(JsonCupCatalog cups, JsonMapCatalog maps, ServerSettings settings) {
        CatalogConsistency.requireEveryCupMapResolves(cups, maps);
        return CupResolution.resolve(cups, settings.cupName());
    }

    @Provides
    @Singleton
    MapInstances mapInstances(InstanceManager instances, ServerSettings settings) {
        return new MapInstances(instances, settings.worldsPath());
    }

    @Provides
    @Singleton
    RaceRuns raceRuns() {
        return new RaceRuns();
    }

    @Provides
    @Singleton
    MapTransition mapTransition(MapInstances instances, RaceRuns runs) {
        return new MapTransition(instances, runs);
    }

    @Provides
    @Singleton
    FlightTracker flightTracker() {
        return new FlightTracker();
    }

    @Provides
    @Singleton
    RaceTimings raceTimings(ServerSettings settings) {
        return settings.timings();
    }

    /**
     * Everyone online, re-read on every call rather than captured once — the roster changes under the
     * tick loop, and a snapshot taken at wiring time is a roster that is empty forever.
     */
    @Provides
    @Singleton
    Supplier<Collection<Player>> onlinePlayers() {
        return () -> MinecraftServer.getConnectionManager().getOnlinePlayers();
    }

    @Provides
    @Singleton
    CupSession cupSession(CupDefinition cup, MapCatalog maps, MapInstances instances,
            MapTransition transition, RaceRuns runs, FlightTracker tracker, RaceTimings timings,
            Supplier<Collection<Player>> players) {
        // MinecraftServer.TICK_MS is what the server actually ticks at, so the phase driver's idea of
        // how much time a tick stands for and the server's cannot drift apart.
        return CupSession.create(cup, maps, instances, transition, runs, tracker, timings,
                Duration.ofMillis(MinecraftServer.TICK_MS), players);
    }
}
