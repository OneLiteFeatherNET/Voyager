package net.elytrarace.voyager.server;

import io.avaje.inject.BeanScope;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.text.VoyagerTranslator;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.RaceCore;
import net.elytrarace.voyager.server.command.ConsoleCommandReader;
import net.elytrarace.voyager.server.command.RaceCommand;
import net.elytrarace.voyager.server.command.ReloadPermission;
import net.elytrarace.voyager.server.config.ConfigCheck;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.flight.Racers;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.item.Material;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.TaskSchedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
 *       graph asks for an {@code InstanceManager}.</li>
 *   <li><strong>The graph, built eagerly.</strong> {@link #openGraph} constructs every singleton now,
 *       which is what turns a malformed map file or a cup naming a map nothing provides into a boot
 *       failure instead of a surprise ten minutes into a rotation.</li>
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
 * {@code CupScorer}, {@code MapScorer}, {@code PlacementBonus} — and until the cup moved out of this
 * module it called three of them directly ({@code MapScorer}, {@code CupScorer}, {@code
 * PlacementBonus}; the cup's scoring now lives in {@code race.cup}). <strong>The annotation stays
 * where it is, and the rule below is why.</strong>
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
        // The validate-and-exit run returns before any game-server state is touched. See configCheck.
        if (Boolean.getBoolean(ConfigCheck.CHECK_PROPERTY)) {
            System.exit(configCheck(args, ConfigCheck.systemProperties(), VoyagerServer::initialisedInstances, System.out));
            return;
        }
        // Settings first, and all of them: a bad directory and a bad port are both reported, not the first.
        Map<String, String> properties = ConfigCheck.systemProperties();
        List<ConfigProblem> settingsProblems = ConfigCheck.settingsProblems(args, properties);
        if (!settingsProblems.isEmpty()) {
            refuse(settingsProblems);
            return;
        }
        ServerSettings settings = ConfigCheck.settingsOf(args, properties);
        LOGGER.info("Voyager (rebuild) — race model v{}, {}", RaceCore.MODEL_VERSION, settings.describe());

        // Before anything can say anything — and the flag is checked before the bundle is even read,
        // because without it reading the bundle is pointless.
        //
        // Registering the bundle is NOT the whole of the wiring, contrary to what this comment used
        // to claim. PlayerSocketConnection.writePacketSync gates the entire translation path on
        // ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION, which reads the system property
        // `minestom.automatic-component-translation` and defaults to FALSE. With it off, every
        // translatable goes to the client untranslated and the player reads `voyager.map.banner`
        // instead of a sentence — which is exactly what shipped, and what a screenshot found rather
        // than a test.
        //
        // The flag is `static final`, read once when ServerFlag first loads, so System.setProperty
        // here would be a race against class loading rather than a fix. It belongs in the JVM's own
        // arguments, and the run tasks set it. Refusing to start is the point: the failure is
        // otherwise silent, cosmetic-looking and permanent.
        if (!ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION) {
            LOGGER.error("Voyager refused to start: Minestom's automatic component translation is off, "
                    + "so every message would reach players as a raw translation key. Start the JVM with "
                    + "-Dminestom.automatic-component-translation=true (the Gradle run tasks do).");
            System.exit(1);
            return;
        }

        VoyagerTranslator translations;
        try {
            translations = VoyagerTranslator.fromClasspath();
            translations.install();
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start: {}", exception.getMessage());
            System.exit(1);
            return;
        }
        LOGGER.info("Loaded {} message(s) from {}", translations.size(), VoyagerTranslator.BUNDLE_RESOURCE);

        MinecraftServer server = MinecraftServer.init();

        // Every problem that concerns the played cup, the maps or the worlds, at once, before the graph is
        // built: boot refuses with the whole report rather than the first problem the graph trips over. A
        // broken cup that is not played is not in it; the cup bean logs that one as a warning.
        List<ConfigProblem> refusals = ConfigCheck.bootRefusals(settings, MinecraftServer.getInstanceManager());
        if (!refusals.isEmpty()) {
            refuse(refusals);
            return;
        }

        BeanScope graph;
        try {
            // Every singleton is built now, so a malformed map file, a duplicate definition or a cup
            // naming a map nothing provides is a refusal here rather than an exception on whichever
            // tick first asked for it.
            graph = openGraph(settings);
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start while building the object graph", exception);
            System.exit(1);
            return;
        }

        CatalogHolder catalog = graph.get(CatalogHolder.class);
        MapInstances instances = graph.get(MapInstances.class);
        CupSession session = graph.get(CupSession.class);
        TickPipeline pipeline = graph.get(TickPipeline.class);
        WaitingRoom waitingRoom = graph.get(WaitingRoom.class);

        try {
            openEveryWorld(catalog.current().rotation(), instances);
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start: a world the cup plays could not be opened", exception);
            instances.close();
            System.exit(1);
            return;
        }

        registerEvents(session, waitingRoom, settings, catalog, instances);
        MinecraftServer.getCommandManager().register(graph.get(RaceCommand.class));
        LOGGER.info("/race reload is registered: the console and operators at level {} may run it",
                ReloadPermission.REQUIRED_LEVEL);
        if (settings.devMode()) {
            LOGGER.warn("Dev mode: short lobby and results screen, and /race start and /race skip are registered");
        }
        scheduleTick(pipeline, waitingRoom, session);
        // Built now so the shutdown task can stop it; started only once the server listens, because a command
        // typed before then would reach a server that is not yet able to take one.
        ConsoleCommandReader console = new ConsoleCommandReader(
                System.in,
                ConsoleCommandReader.consoleOf(MinecraftServer.getCommandManager()),
                line -> MinecraftServer.getCommandManager().getConsoleSender().sendMessage(line));
        registerShutdownTask(graph, instances, catalog, console);

        LOGGER.info("Listening on {}:{}", settings.host(), settings.port());
        server.start(settings.host(), settings.port());
        console.start();
        LOGGER.info("Voyager started. {}", session.describe().trim());
    }

    /**
     * The validate-and-exit run, returning its exit code: 0 for a configuration with no error, 1 for one with
     * any. The report goes to {@code out}, one line per problem.
     *
     * <p>The settings are checked before Minestom is initialised, so a refused configuration never touches
     * it. {@code instances} is asked for only after that, and the world check is the one caller that uses
     * it. Nothing here starts the game server or binds a socket.
     *
     * @param instances supplies the instance manager of an initialised Minestom server; {@link #main} passes
     *     the one that initialises it
     */
    static int configCheck(String[] args, Map<String, String> properties,
            Supplier<InstanceManager> instances, PrintStream out) {
        List<ConfigProblem> settingsProblems = ConfigCheck.settingsProblems(args, properties);
        if (!settingsProblems.isEmpty()) {
            return ConfigCheck.report(settingsProblems, out);
        }
        return ConfigCheck.run(ConfigCheck.settingsOf(args, properties), instances.get(), out);
    }

    /** Initialises Minestom's registries, which the world loader needs, and returns the instance manager. */
    private static InstanceManager initialisedInstances() {
        MinecraftServer.init();
        return MinecraftServer.getInstanceManager();
    }

    /**
     * Ends a refused start with the problems logged, one per line, and exit status 1.
     */
    private static void refuse(List<ConfigProblem> problems) {
        for (ConfigProblem problem : problems) {
            LOGGER.error("Voyager refused to start: {}", problem.format());
        }
        System.exit(1);
    }

    /**
     * Builds the object graph for {@code settings}, with every singleton constructed before this returns.
     *
     * <p>Eager construction is what turns a malformed map file, a duplicate definition or a cup naming a
     * map nothing provides into a refusal here, rather than an exception on whichever tick first asked for
     * it. The scope is built without a JVM shutdown hook: {@code main} closes it from Minestom's own
     * shutdown task, where it is ordered with the world handles instead of racing them.
     *
     * @return the scope; the caller closes it when the server stops
     * @throws IllegalStateException if a bean cannot be built; the message and the cause name the failing
     *     bean or configured value
     */
    static BeanScope openGraph(ServerSettings settings) {
        try {
            return BeanScope.builder()
                    .bean(ServerSettings.class, settings)
                    .shutdownHook(false)
                    .build();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "the object graph did not build: %s".formatted(exception.getMessage()), exception);
        }
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
    private static void openEveryWorld(List<MapDefinition> rotation, MapInstances instances) {
        if (rotation.isEmpty()) {
            throw new IllegalStateException("the cup has no maps; CupDefinition refuses an empty rotation");
        }
        for (MapDefinition map : rotation) {
            instances.forWorld(map.world());
            LOGGER.info("Opened world '{}' for map '{}' ({} rings, spawn {})",
                    map.world(), map.name(), map.rings().size(), map.spawn());
        }
    }

    private static void registerEvents(CupSession session, WaitingRoom waitingRoom, ServerSettings settings,
            CatalogHolder catalog, MapInstances instances) {
        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            // Players spawn straight into the first map's world of the current catalogue. There is no lobby
            // world in the rebuild: there is no lobby map data, and generating a flat one would be a second world
            // nobody asked for standing between a tester and the thing being tested. The current catalogue, not
            // the boot one, so a reload that was promoted at a round start is the one a new joiner sees.
            MapDefinition first = catalog.current().rotation().getFirst();
            event.setSpawningInstance(instances.forWorld(first.world()));
            event.getPlayer().setRespawnPoint(Vectors.toMinestom(first.spawn()).asPos());
        });

        events.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                return;
            }
            // Prepared without flight equipment: a waiting racer stands on the spawn and cannot fly off it.
            // The room decides whether this join starts, joins or waits for a cup, and says so to the racer.
            Racers.prepare(event.getPlayer());
            LOGGER.info("{} joined", event.getPlayer().getUsername());
            waitingRoom.joined(event.getPlayer());
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
            LOGGER.info("Join with a 26.2 client; the cup starts once {} racer(s) are online, and /race shows where it stands",
                    settings.minimumRacers());
        }
    }

    /**
     * One run of the cup's {@link TickPipeline} per server tick, at {@link ExecutionType#TICK_START}.
     *
     * <p>The guard is not decoration. A tick that throws throws again on the next one, twenty times a
     * second, and the stack trace that mattered is then the one at the top of a hundred thousand
     * identical ones. So the first failure stops the cup and is logged once, with everything a
     * {@code /race} afterwards can still be asked about left standing.
     */
    private static void scheduleTick(TickPipeline pipeline, WaitingRoom waitingRoom, CupSession session) {
        AtomicBoolean broken = new AtomicBoolean();
        MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            if (broken.get()) {
                return;
            }
            try {
                pipeline.run();
                // After the cup's own tick, inside the same guard: the room reads the cup as this tick left it.
                waitingRoom.tick();
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
    private static void registerShutdownTask(BeanScope graph, MapInstances instances, CatalogHolder catalog,
            ConsoleCommandReader console) {
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            LOGGER.info("Shutting down");
            console.stop();
            for (MapDefinition map : catalog.current().rotation()) {
                LOGGER.info("Final world health — {}", instances.healthOf(map.world()).describe());
            }
            try {
                instances.close();
                LOGGER.info("World handles closed; nothing was saved, by design");
            } catch (RuntimeException exception) {
                LOGGER.warn("A world handle did not close cleanly", exception);
            }
            // After the world handles, so a bean's preDestroy hook, should one ever be added, runs
            // once nothing is still reading a world through it.
            graph.close();
        });
    }
}
