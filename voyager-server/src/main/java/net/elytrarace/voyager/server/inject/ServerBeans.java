package net.elytrarace.voyager.server.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.race.CupCatalog;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.platform.catalog.CatalogConsistency;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.catalog.CatalogReading;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>{@link #catalogReading} refuses on any problem that is not a single cup file, and {@link #cup}
 * refuses on the played cup's own dangling map entries, so a cup naming a map nothing provides fails
 * while the graph is being built rather than the first time that map comes up in the rotation, minutes
 * after anybody was watching. A broken cup that is not played does not refuse; it is logged once.
 */
@Factory
public final class ServerBeans {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerBeans.class);

    @Bean
    InstanceManager instanceManager() {
        return MinecraftServer.getInstanceManager();
    }

    /**
     * The data directory, read once, with every problem in it. Boot refuses on a problem in the
     * directories or in a map file, which is the first-problem policy the maps have always had. A
     * problem in a cup file is left for {@link #cup}, which decides whether that cup is the one played.
     */
    @Bean
    CatalogReading catalogReading(@External ServerSettings settings) {
        CatalogReading reading = CatalogLoader.read(settings.dataPath());
        if (!reading.catalogueProblems().isEmpty()) {
            throw reading.catalogueProblems().getFirst().cause();
        }
        return reading;
    }

    /** The catalogue as the ports see it: every map and cup that parsed. */
    @Bean
    CatalogSnapshot catalog(CatalogReading reading) {
        return reading.snapshot();
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

    /**
     * The cup this server plays. The order is the boot policy: resolve the chosen cup first, so a typo
     * names the cups that exist; refuse if the played cup names a map nothing provides, listing only its
     * own entries; then log one warning for every other cup that cannot be played.
     */
    @Bean
    CupDefinition cup(CatalogReading reading, @External ServerSettings settings) {
        CupDefinition played = CupResolution.resolve(reading, settings.cupName());
        CatalogSnapshot catalog = reading.snapshot();
        Optional<UnresolvedCupMapException> unresolved = CatalogConsistency.unresolvedCupMaps(
                catalog.maps(), Map.of(played.name(), played));
        if (unresolved.isPresent()) {
            throw unresolved.get();
        }
        List<String> skipped = CupResolution.skippedCups(reading, played);
        if (!skipped.isEmpty()) {
            LOGGER.warn(CupResolution.skippedWarning(skipped));
        }
        return played;
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
