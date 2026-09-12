package net.elytrarace.voyager.server;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.RaceCore;
import net.elytrarace.voyager.server.command.RaceCommand;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.game.CupSession;
import net.elytrarace.voyager.server.game.Racers;
import net.elytrarace.voyager.server.inject.VoyagerModule;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.Material;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.TaskSchedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The composition root: the one {@code main} in the rebuild, and the only place that knows all of it
 * exists at once.
 *
 * <p>Usage: {@code java -jar voyager-server.jar [host] [port]}, or {@code ./gradlew
 * :voyager-server:runServerDev} / {@code :voyager-server:runServer}. Configuration is
 * {@link ServerSettings}; there is no configuration file, and everything it reads is named there.
 *
 * <h2>The order, which is the only interesting thing in this file</h2>
 *
 * <ol>
 *   <li><strong>Settings first.</strong> Both directories are checked before anything else runs, so
 *       a wrong working directory is a one-line refusal naming the absolute path rather than a
 *       stack trace forty lines into a server log.</li>
 *   <li><strong>{@code MinecraftServer.init()}.</strong> The registries have to exist before the
 *       injector asks for an {@code InstanceManager}.</li>
 *   <li><strong>The graph, in {@link Stage#PRODUCTION}.</strong> Guice then instantiates every
 *       singleton eagerly, which is what turns a malformed map file or a cup naming a map nothing
 *       provides into a boot failure instead of a surprise ten minutes into a rotation.</li>
 *   <li><strong>Every world the cup plays, resolved and reported.</strong> {@code MapInstances}
 *       refuses a world with no region data behind it, so this is where "that world is not on disk"
 *       lands — before a client can connect, rather than when the map comes up.</li>
 *   <li><strong>Events, command, tick task, then listen.</strong> A connection that arrives on the
 *       first millisecond of {@code start} finds the join handler already registered.</li>
 * </ol>
 *
 * <h2>On {@code @ApiStatus.Internal}, which this stage had to decide once</h2>
 *
 * <p>Seven of {@code voyager-race}'s entry points carry {@code @ApiStatus.Internal} —
 * {@code RaceCore}, {@code RingPass}, {@code RaceStateMachine}, {@code ProgressTracker},
 * {@code CupScorer}, {@code MapScorer}, {@code PlacementBonus} — and this module calls three of them
 * directly ({@code MapScorer}, {@code CupScorer}, {@code PlacementBonus}, all from
 * {@code net.elytrarace.voyager.server.game}). <strong>The annotation stays where it is and this
 * module ignores it deliberately.</strong>
 *
 * <p>Two reasons, and the second is the one that settles it.
 *
 * <p>The annotation warns a consumer outside the project that a type is not API and may change
 * without notice. A composition root inside the project is not that consumer; it is the code the
 * annotation exists to protect, not the code it exists to warn. Removing it so that this module can
 * call without a marker would remove the warning for everybody outside as well, which is the
 * opposite of what it is for.
 *
 * <p>And the asymmetry the plan asked about — that {@code RaceRun}, {@code RingProgress},
 * {@code MapScore} and {@code RaceClock} do <em>not</em> carry it — <strong>is a rule, not an
 * accident</strong>. The annotation sits on every abstract utility class in {@code voyager-race} and
 * on none of its records. That is design rule 2 ("Factory as abstract utility class — abstract,
 * private constructor, {@code @ApiStatus.Internal}") applied exactly: the marked types are
 * <em>operations</em>, and the unmarked ones are the <em>values</em> those operations produce.
 * Values cross module boundaries by design — {@code RacePhaseListener} hands a {@code RaceClock}
 * across one on every tick — and a value a neighbouring module is required to hold cannot be
 * internal. Stripping the annotation off the seven would break design rule 2 for every one of them;
 * ignoring it in one module breaks nothing.
 *
 * <p>So: it is not left as "both". It is documented, here, once, and the rule it follows is named so
 * that the next type to get the annotation gets it for a reason rather than by imitation.
 */
public final class VoyagerServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoyagerServer.class);

    private VoyagerServer() {
    }

    public static void main(String[] args) {
        ServerSettings settings;
        try {
            settings = ServerSettings.fromEnvironment(args);
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start: {}", exception.getMessage());
            System.exit(1);
            return;
        }
        LOGGER.info("Voyager (rebuild) — race model v{}, {}", RaceCore.MODEL_VERSION, settings.describe());

        MinecraftServer server = MinecraftServer.init();

        Injector injector;
        try {
            // PRODUCTION, not the default DEVELOPMENT: every singleton is built now, so a malformed
            // map file, a duplicate definition or a cup naming a map nothing provides is a refusal
            // here rather than an exception on whichever tick first asked for it.
            injector = Guice.createInjector(Stage.PRODUCTION, new VoyagerModule(settings));
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start while building the object graph", exception);
            System.exit(1);
            return;
        }

        CupDefinition cup = injector.getInstance(CupDefinition.class);
        MapCatalog maps = injector.getInstance(MapCatalog.class);
        MapInstances instances = injector.getInstance(MapInstances.class);
        CupSession session = injector.getInstance(CupSession.class);

        List<MapDefinition> rotation;
        Instance firstWorld;
        try {
            rotation = resolveRotation(cup, maps);
            firstWorld = openEveryWorld(rotation, instances);
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start: a world the cup plays could not be opened", exception);
            instances.close();
            System.exit(1);
            return;
        }

        Pos firstSpawn = Vectors.toMinestom(rotation.getFirst().spawn()).asPos();
        registerEvents(session, settings, firstWorld, firstSpawn);
        MinecraftServer.getCommandManager().register(new RaceCommand(session, settings.devMode()));
        if (settings.devMode()) {
            LOGGER.warn("Dev mode: short lobby and results screen, and /race start and /race skip are registered");
        }
        scheduleTick(session);
        registerShutdownTask(instances, rotation);

        LOGGER.info("Listening on {}:{}", settings.host(), settings.port());
        server.start(settings.host(), settings.port());
        LOGGER.info("Voyager started. {}", session.describe().trim());
    }

    /**
     * The cup's maps, in rotation order. {@code CatalogConsistency} has already run inside the
     * injector, so every name resolves; the {@code orElseThrow} is the assertion that it did, not a
     * branch anybody takes.
     */
    private static List<MapDefinition> resolveRotation(CupDefinition cup, MapCatalog maps) {
        List<MapDefinition> rotation = new ArrayList<>(cup.mapNames().size());
        for (String name : cup.mapNames()) {
            rotation.add(maps.byName(name).orElseThrow(() -> new IllegalStateException(
                    "cup '%s' plays a map named '%s' the catalogue does not hold".formatted(cup.name(), name))));
        }
        return List.copyOf(rotation);
    }

    /**
     * Opens every world the cup will need and returns the first map's instance, which is also where
     * players spawn.
     *
     * <p>All of them, not just the first: {@code MapInstances.forWorld} is what refuses a world with
     * no region data behind it, and finding that out about map three when map three comes up is
     * finding it out in front of an audience. The cost is one Falco loader per world, held open for
     * the life of the server, which is what a race server does anyway.
     */
    private static Instance openEveryWorld(List<MapDefinition> rotation, MapInstances instances) {
        Instance first = null;
        for (MapDefinition map : rotation) {
            Instance instance = instances.forWorld(map.world());
            if (first == null) {
                first = instance;
            }
            LOGGER.info("Opened world '{}' for map '{}' ({} rings, spawn {})",
                    map.world(), map.name(), map.rings().size(), map.spawn());
        }
        if (first == null) {
            throw new IllegalStateException("the cup has no maps; CupDefinition refuses an empty rotation");
        }
        return first;
    }

    private static void registerEvents(CupSession session, ServerSettings settings,
            Instance firstWorld, Pos firstSpawn) {
        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            // Players spawn straight into the first map's world. There is no lobby world in the
            // rebuild: there is no lobby map data, and generating a flat one would be a second world
            // nobody asked for standing between a tester and the thing being tested.
            event.setSpawningInstance(firstWorld);
            event.getPlayer().setRespawnPoint(firstSpawn);
        });

        events.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                return;
            }
            Racers.prepare(event.getPlayer());
            LOGGER.info("{} joined", event.getPlayer().getUsername());
            if (!session.running()) {
                // The first player starts the cup. Started at boot instead, it would play its whole
                // rotation to an empty world while the first client was still connecting — see
                // CupSession's class javadoc.
                LOGGER.info("First player online — starting cup '{}'", session.cup().name());
                session.start(false);
            }
        });

        events.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getItemStack().material() != Material.FIREWORK_ROCKET) {
                return;
            }
            // Cancelled whatever the answer, and deliberately. Minestom would not consume the rocket
            // on its own — a firework carries no CONSUMABLE component — but cancelling makes the
            // server's position explicit and, through the inventory resync the cancel triggers, keeps
            // a client that predicted a consumption from drifting a rocket ahead of the stack it
            // actually holds. The stack is never spent: the cooldown is what limits a racer.
            event.setCancelled(true);
            session.requestBoost(event.getPlayer());
        });

        events.addListener(PlayerDisconnectEvent.class, event -> {
            session.forget(event.getPlayer().getUuid());
            LOGGER.info("{} left", event.getPlayer().getUsername());
        });

        if (settings.devMode()) {
            LOGGER.info("Join with a 26.2 client and the cup starts on its own; /race shows where it stands");
        }
    }

    /**
     * One {@link CupSession#tick()} per server tick, at {@link ExecutionType#TICK_START}.
     *
     * <p>The guard is not decoration. A tick that throws throws again on the next one, twenty times a
     * second, and the stack trace that mattered is then the one at the top of a hundred thousand
     * identical ones. So the first failure stops the cup and is logged once, with everything a
     * {@code /race} afterwards can still be asked about left standing.
     */
    private static void scheduleTick(CupSession session) {
        AtomicBoolean broken = new AtomicBoolean();
        MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            if (broken.get()) {
                return;
            }
            try {
                session.tick();
            } catch (RuntimeException exception) {
                broken.set(true);
                session.stop();
                LOGGER.error("The race tick threw; the cup has been stopped. /race still reports the "
                        + "state it stopped in.", exception);
            }
        }, TaskSchedule.nextTick(), TaskSchedule.nextTick(), ExecutionType.TICK_START);
    }

    /**
     * Closes the world handles as part of Minestom's own shutdown.
     *
     * <p><strong>A {@code Runtime.addShutdownHook} of our own does not work here, and this is the
     * second version.</strong> Minestom installs its own hook
     * ({@code ServerFlag.SHUTDOWN_ON_SIGNAL}) and Log4j installs a third; JVM shutdown hooks run
     * concurrently, and the first run of this server on a {@code SIGTERM} printed "Shutting down" and
     * then nothing at all — not the world health, not Minestom's own "server stopped successfully" —
     * because the logging context had been torn down underneath both of them. So the work moved
     * inside Minestom's shutdown, where it is ordered rather than racing, and {@code log4j2.xml}
     * turns Log4j's hook off so the last thing a shutdown does can still be read.
     *
     * <p>{@code MapInstances.close()} closes Falco's region handles and <strong>does not save</strong>.
     * A race server reads maps and never writes them, so there is no state worth persisting — and a
     * save path is precisely how a bug anywhere else turns into a corrupted racetrack nobody notices
     * until a player flies into the hole.
     */
    private static void registerShutdownTask(MapInstances instances, List<MapDefinition> rotation) {
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            LOGGER.info("Shutting down");
            for (MapDefinition map : rotation) {
                LOGGER.info("Final world health — {}", instances.healthOf(map.world()).describe());
            }
            try {
                instances.close();
                LOGGER.info("World handles closed; nothing was saved, by design");
            } catch (RuntimeException exception) {
                LOGGER.warn("A world handle did not close cleanly", exception);
            }
        });
    }
}
