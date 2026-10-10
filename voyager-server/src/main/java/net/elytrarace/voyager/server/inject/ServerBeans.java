package net.elytrarace.voyager.server.inject;

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.elytrarace.voyager.platform.catalog.CatalogReloader;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.server.CatalogReloadService;
import net.elytrarace.voyager.server.command.RaceCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceManager;

import java.time.Clock;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * The services of the server that are not the race's own: Minestom's instance manager, the catalogue
 * holder the race reads, the world handles, and the live roster.
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
 * <p>{@link #catalogHolder} refuses boot on the catalogue's first problem and on the played cup's own
 * problems, so a cup naming a map nothing provides fails while the graph is being built rather than the
 * first time that map comes up in the rotation. A broken cup that is not played does not refuse; the
 * reader logs it once.
 */
@Factory
public final class ServerBeans {

    @Bean
    InstanceManager instanceManager() {
        return MinecraftServer.getInstanceManager();
    }

    /** The clock a catalogue load is stamped with. The tests of the reloader use a fixed one instead. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Reads the data directory into a catalogue, and refuses boot with the first problem, as boot always has. The
     * world opener is {@link MapInstances}, which is built below and is the same instance the race uses.
     */
    @Bean
    CatalogReloader catalogReloader(Clock clock, MapInstances instances) {
        return new CatalogReloader(clock, instances);
    }

    /**
     * The one catalogue holder. It is built from the boot catalogue, which is the only value a bean of a
     * catalogue type would ever freeze; every consumer reads the holder, and the holder changes content and never
     * identity.
     */
    @Bean
    CatalogHolder catalogHolder(CatalogReloader reloader, @External ServerSettings settings) {
        return new CatalogHolder(reloader.loadInitial(settings.dataPath(), settings.cupName()));
    }

    /**
     * The operator's reload: re-reads the data directory on a virtual thread, so the tick never waits for it, and
     * offers an applied catalogue to the holder.
     */
    @Bean
    CatalogReloadService catalogReloadService(CatalogReloader reloader, CatalogHolder holder,
            @External ServerSettings settings) {
        return new CatalogReloadService(() -> reloader.reload(settings.dataPath(), settings.cupName()), holder,
                Thread::startVirtualThread);
    }

    /**
     * The operator's race command, built here with the cup it drives and the reload it offers. Its constructor only
     * adds syntax and registers nothing; {@code VoyagerServer} registers it when the server is ready to take commands.
     */
    @Bean
    RaceCommand raceCommand(CupSession session, WaitingRoom room, CatalogReloadService reloads,
            @External ServerSettings settings) {
        return new RaceCommand(session, room, settings.devMode(), reloads);
    }

    /**
     * The waiting room: starts, cancels and aborts the cup from the racers online, with the minimum the settings
     * name. A finished cup places its racers at the first map's spawn, which is the spawn a new joiner gets.
     */
    @Bean
    WaitingRoom waitingRoom(CupSession session, Supplier<Collection<Player>> players, CatalogHolder catalog,
            MapInstances instances, @External ServerSettings settings) {
        return new WaitingRoom(session, players, settings.minimumRacers(), racer -> {
            MapDefinition first = catalog.current().rotation().getFirst();
            racer.setInstance(instances.forWorld(first.world()), Vectors.toMinestom(first.spawn()).asPos());
        });
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
