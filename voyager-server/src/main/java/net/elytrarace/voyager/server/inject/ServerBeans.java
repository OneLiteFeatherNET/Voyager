package net.elytrarace.voyager.server.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.race.CupCatalog;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
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
 * The services of the server that are not the race's own: Minestom's instance manager, the catalogue
 * and the ports it answers, the cup chosen from it, the world handles, and the live roster.
 *
 * <p>This is the half of the graph that serves the server rather than the race. Every bean is a
 * singleton, which is avaje's default for a {@code @Bean}.
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
 * <p>{@link #catalog} runs the cross-catalogue consistency check inside {@link CatalogLoader#load}, so a
 * cup naming a map nothing provides fails while the graph is being built rather than the first time
 * that map comes up in the rotation, minutes after anybody was watching.
 */
@Factory
public final class ServerBeans {

    @Bean
    InstanceManager instanceManager() {
        return MinecraftServer.getInstanceManager();
    }

    /**
     * The catalogue: every map and cup in the data directory, read once. {@link CatalogLoader#load}
     * also runs the cross-catalogue check, so a cup naming a map nothing provides refuses boot here.
     */
    @Bean
    CatalogSnapshot catalog(@External ServerSettings settings) {
        return CatalogLoader.load(settings.dataPath());
    }

    /**
     * The map port, answered by the snapshot. A method reference rather than a second catalogue: the
     * snapshot stays the one owner of the data.
     */
    @Bean
    MapCatalog mapCatalog(CatalogSnapshot catalog) {
        return catalog::mapByName;
    }

    /** The cup port, answered by the snapshot. */
    @Bean
    CupCatalog cupCatalog(CatalogSnapshot catalog) {
        return catalog::cupByName;
    }

    /** The cup this server plays, chosen from the snapshot by {@link CupResolution}. */
    @Bean
    CupDefinition cup(CatalogSnapshot catalog, @External ServerSettings settings) {
        return CupResolution.resolve(catalog, settings.cupName());
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
