package net.elytrarace.voyager.server;

import io.avaje.inject.BeanScope;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.permission.PermissionNode;
import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.text.VoyagerTranslator;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.RaceCore;
import net.elytrarace.voyager.platform.lifecycle.ConsoleCommandReader;
import net.elytrarace.voyager.platform.lifecycle.ServiceShutdown;
import net.elytrarace.voyager.platform.lifecycle.StopCommand;
import net.elytrarace.voyager.platform.permission.luckperms.LuckPermsBootstrap;
import net.elytrarace.voyager.platform.player.VoyagerPlayerProvider;
import net.elytrarace.voyager.platform.permission.luckperms.exception.PermissionBackendStartException;
import net.elytrarace.voyager.platform.config.ConfigLoader;
import net.elytrarace.voyager.platform.proxy.ProxyForwardingSettings;
import net.elytrarace.voyager.platform.proxy.VelocityAuth;
import net.elytrarace.voyager.platform.stats.StatsRequests;
import net.elytrarace.voyager.server.command.LeaderboardCommand;
import net.elytrarace.voyager.server.command.RaceCommand;
import net.elytrarace.voyager.server.command.StatsCommand;
import net.elytrarace.voyager.server.config.ConfigCheck;
import net.elytrarace.voyager.server.config.PersistenceSettings;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.inject.JoinArrival;
import net.elytrarace.voyager.server.persistence.StatsBinding;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.lobby.WaitingRoom;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.flight.Racers;
import net.hollowcube.minestom.extensions.ExtensionBootstrap;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The composition root: the one {@code main} in the rebuild, and the only place that knows all of it
 * exists at once.
 *
 * <p>Usage: {@code java -jar voyager-server.jar [host] [port]}, or {@code ./gradlew
 * :voyager:server:runServerDev} / {@code :voyager:server:runServer}. Configuration is
 * {@link ServerSettings}, read through {@code ConfigLoader} (ADR-0036); every key it reads is named in {@code ServerSettings}.
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

    /** Every key the configuration check reads: the server's settings and the database's. */
    private static final List<String> CHECK_KEYS = Stream.concat(ServerSettings.KEYS.stream(),
            PersistenceSettings.KEYS.stream()).toList();

    /** The property Minestom's {@code ServerFlag} reads, once, when that class first loads. */
    static final String AUTOMATIC_TRANSLATION_PROPERTY = "minestom.automatic-component-translation";

    /**
     * Turns on Minestom's automatic component translation. It has to run before anything touches
     * {@code ServerFlag}, which is why {@link #main} calls it as its first statement.
     */
    static void enableAutomaticComponentTranslation() {
        System.setProperty(AUTOMATIC_TRANSLATION_PROPERTY, "true");
    }

    public static void main(String[] args) {
        enableAutomaticComponentTranslation();
        // Every setting is read through one loader (ADR-0036): the working directory's application.yaml, CONFIG_FILE, the
        // environment and the system properties, in that order of precedence.
        ConfigLoader config = ConfigLoader.fromJvm();
        // The validate-and-exit run returns before any game-server state is touched. See configCheck.
        if (Boolean.parseBoolean(config.get(ConfigCheck.CHECK_PROPERTY))) {
            System.exit(configCheck(args, config, VoyagerServer::initialisedInstances, System.out));
            return;
        }
        // Settings first, and all of them: a bad directory and a bad port are both reported, not the first.
        Map<String, String> properties = config.view(ServerSettings.KEYS);
        List<ConfigProblem> settingsProblems = ConfigCheck.settingsProblems(args, properties, ConfigCheck.sourcesOf(config));
        if (!settingsProblems.isEmpty()) {
            refuse(settingsProblems);
            return;
        }
        ServerSettings settings = ConfigCheck.settingsOf(args, properties);
        LOGGER.info("Voyager (rebuild) — race model v{}, {}", RaceCore.MODEL_VERSION, settings.describe());
        ConfigCheck.comBootLine(settings).ifPresent(LOGGER::info);
        // The database settings are read once, here, and never refuse the start: each problem is one warning, and the server
        // runs with statistics off (owner decision Q1). Unset or off is one warning that no statistics are kept.
        PersistenceSettings persistence = PersistenceSettings.from(config);
        for (ConfigProblem problem : persistence.warnings()) {
            LOGGER.warn(problem.message());
        }

        // Before anything can say anything. PlayerSocketConnection.writePacketSync gates the entire
        // translation path on ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION, which is read once, when
        // ServerFlag first loads. Without it every translatable reaches the client untranslated and the
        // player reads `voyager.map.banner` instead of a sentence. main() set the property before any
        // Minestom class loaded, so the JVM argument is not needed. This guard refuses to start if that
        // ordering was ever broken, because the failure would otherwise be silent and permanent.
        if (!ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION) {
            LOGGER.error("Voyager refused to start: Minestom's automatic component translation is off, "
                    + "so every message would reach players as a raw translation key. ServerFlag loaded "
                    + "before %s was set.".formatted(AUTOMATIC_TRANSLATION_PROPERTY));
            System.exit(1);
            return;
        }

        VoyagerTranslator translations;
        try {
            translations = VoyagerTranslator.fromClasspath(VoyagerTranslator.BUNDLE);
            translations.install();
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start: {}", exception.getMessage());
            System.exit(1);
            return;
        }
        LOGGER.info("Loaded {} message(s) in {} from {}", translations.size(), translations.tags(), VoyagerTranslator.BUNDLE);
        translations.untranslated().forEach((tag, keys) -> keys.forEach(key -> LOGGER.warn(
                "Message {} has no {} line, so players in that locale are shown English", key, tag)));
        // A player whose settings have not arrived yet gets the Minestom default, which is the JVM default. On a German
        // host that would be German for a player who has not said so; English is the answer the fallback chain gives.
        MinestomAdventure.setDefaultLocale(Locale.US);

        // Velocity forwarding is decided before Minestom starts, because the auth is fixed at init. A blank secret
        // refuses to start rather than running offline; no secret runs offline with a WARN (ADR-0025).
        Auth auth;
        try {
            ProxyForwardingSettings forwarding = ProxyForwardingSettings.fromLoader(config);
            auth = VelocityAuth.of(forwarding);
            if (auth == null) {
                LOGGER.warn("No Velocity secret is set ({} or -D{}): the server runs in offline mode and player UUIDs are "
                        + "not forwarded from a proxy. LuckPerms cannot find a proxied player's grants in that case.",
                        ProxyForwardingSettings.ENVIRONMENT_NAME, ProxyForwardingSettings.PROPERTY_NAME);
            }
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Voyager refused to start: {}", exception.getMessage());
            System.exit(1);
            return;
        }
        // The extension bootstrap initialises Minestom as MinecraftServer.init did, and at start it loads extensions/ and binds
        // the port (ADR-0026). A root without extensions/ runs as before, with no bridge.
        ExtensionBootstrap bootstrap = auth == null ? ExtensionBootstrap.init() : ExtensionBootstrap.init(auth);

        // LuckPerms is started only when its loader is on the class path, and before the port binds. A start that
        // throws refuses to listen; it never falls back to the level-based policy (ADR-0024).
        if (LuckPermsBootstrap.isPresent()) {
            try {
                LuckPermsBootstrap.start();
            } catch (PermissionBackendStartException exception) {
                LOGGER.error("Voyager refused to start: {}", exception.getMessage(), exception);
                System.exit(1);
                return;
            }
        }

        // Every problem that concerns the played cup, the maps or the worlds, at once, before the graph is
        // built: boot refuses with the whole report rather than the first problem the graph trips over. A
        // broken cup that is not played is not in it; the cup bean logs that one as a warning.
        List<ConfigProblem> refusals = ConfigCheck.bootRefusals(settings, MinecraftServer.getInstanceManager(),
                ConfigCheck.sourcesOf(config));
        if (!refusals.isEmpty()) {
            refuse(refusals);
            return;
        }

        BeanScope graph;
        try {
            // Every singleton is built now, so a malformed map file, a duplicate definition or a cup
            // naming a map nothing provides is a refusal here rather than an exception on whichever
            // tick first asked for it.
            graph = openGraph(settings, persistence);
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
        if (settings.lobby().isEmpty()) {
            LOGGER.warn(("No lobby world is set (%s and %s): waiting racers stand at the first map's spawn. "
                    + "Set both to give them the lobby world (ADR-0029).")
                    .formatted(ServerSettings.LOBBY_WORLD_PROPERTY, ServerSettings.LOBBY_SPAWN_PROPERTY));
        }

        try {
            openEveryWorld(catalog.current().rotation(), instances);
        } catch (RuntimeException exception) {
            LOGGER.error("Voyager refused to start: a world the cup plays could not be opened", exception);
            instances.close();
            System.exit(1);
            return;
        }

        // Every player is created with the permission pointer, through the one policy bean (ADR-0026, the CloudNet bridge
        // reads it). Installed before the port binds, so no connection can arrive as a plain Minestom player.
        MinecraftServer.getConnectionManager().setPlayerProvider(new VoyagerPlayerProvider(graph.get(PermissionPolicy.class)));
        registerEvents(session, waitingRoom, settings, catalog, instances);
        MinecraftServer.getCommandManager().register(graph.get(RaceCommand.class));
        // Open to every sender: a player looks up their own record or another player's, and the leaderboard is public.
        MinecraftServer.getCommandManager().register(graph.get(StatsCommand.class));
        MinecraftServer.getCommandManager().register(graph.get(LeaderboardCommand.class));
        LOGGER.info("/race reload is registered: gated by {}", PermissionNode.VOYAGER_COMMAND_RACE_RELOAD.value());
        if (settings.devMode()) {
            LOGGER.warn("Dev mode: short lobby and results screen, and /race start and /race skip are registered");
        }
        scheduleTick(pipeline, waitingRoom, session, graph.get(StatsRequests.class));
        // The statistics threads start before the port binds, so a race that finishes on the first tick is written. A
        // database that was down at boot logs its one warning here, and is retried in the background from now on.
        StatsBinding statistics = graph.get(StatsBinding.class);
        statistics.bootWarning().ifPresent(warning -> LOGGER.warn(warning));
        statistics.start();
        // The one clean shutdown: the stdin line `stop` and /stop both end here. The stop runs on a platform thread,
        // because the reader's own thread is the one the stop closes.
        ServiceShutdown shutdown = new ServiceShutdown(() -> {
            MinecraftServer.stopCleanly();
            System.exit(0);
        }, runnable -> Thread.ofPlatform().name("voyager-shutdown").start(runnable));
        MinecraftServer.getCommandManager().register(new StopCommand(graph.get(PermissionPolicy.class), shutdown));
        // Built now so the shutdown task can stop it; started only once the server listens, because a command
        // typed before then would reach a server that is not yet able to take one.
        ConsoleCommandReader console = new ConsoleCommandReader(
                System.in,
                shutdown.consoleDispatcher(ConsoleCommandReader.consoleOf(MinecraftServer.getCommandManager())),
                line -> MinecraftServer.getCommandManager().getConsoleSender().sendMessage(line));
        registerShutdownTask(graph, instances, catalog, console);

        LOGGER.info("Listening on {}:{}", settings.host(), settings.port());
        bootstrap.start(settings.host(), settings.port());
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
    static int configCheck(String[] args, ConfigLoader config,
            Supplier<InstanceManager> instances, PrintStream out) {
        return configCheck(args, config.view(CHECK_KEYS), ConfigCheck.sourcesOf(config), instances, out);
    }

    /** {@link #configCheck(String[], ConfigLoader, Supplier, PrintStream)} over a map of values, labelled as properties. */
    static int configCheck(String[] args, Map<String, String> properties,
            Supplier<InstanceManager> instances, PrintStream out) {
        return configCheck(args, properties, ConfigCheck.PROPERTY_SOURCE, instances, out);
    }

    /**
     * The check's settings: the server's and the database's. The database warnings are printed with the report, and only
     * the server's settings can refuse it, so a database problem does not change the exit code.
     */
    private static int configCheck(String[] args, Map<String, String> properties, Function<String, String> sources,
            Supplier<InstanceManager> instances, PrintStream out) {
        List<ConfigProblem> settingsProblems = ConfigCheck.settingsProblems(args, properties, sources);
        List<ConfigProblem> persistence = ConfigCheck.persistenceProblems(properties, sources);
        if (!settingsProblems.isEmpty()) {
            List<ConfigProblem> all = new ArrayList<>(settingsProblems);
            all.addAll(persistence);
            all.sort(ConfigProblem.ORDER);
            return ConfigCheck.report(all, out);
        }
        return ConfigCheck.run(ConfigCheck.settingsOf(args, properties), instances.get(), out, sources, persistence);
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
        return openGraph(settings, PersistenceSettings.off());
    }

    /**
     * {@link #openGraph(ServerSettings)} with the database settings the boot read. Opening the statistics database is part of
     * building the graph, so a database that is down at boot is the binding's business and never a failure here.
     */
    static BeanScope openGraph(ServerSettings settings, PersistenceSettings persistence) {
        try {
            return BeanScope.builder()
                    .bean(ServerSettings.class, settings)
                    .bean(PersistenceSettings.class, persistence)
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
            // A joining racer spawns in the lobby world when one is configured, and in the first map's world of the
            // current catalogue when not. The current catalogue, not the boot one, so a reload that was promoted at a
            // round start is the one a new joiner sees.
            JoinArrival.Arrival arrival = JoinArrival.of(waitingRoom.lobby(), catalog.current().rotation().getFirst(),
                    instances);
            event.setSpawningInstance(arrival.instance());
            event.getPlayer().setRespawnPoint(arrival.position());
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
            waitingRoom.left(event.getPlayer().getUuid());
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
    private static void scheduleTick(TickPipeline pipeline, WaitingRoom waitingRoom, CupSession session,
            StatsRequests statistics) {
        AtomicBoolean broken = new AtomicBoolean();
        MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            if (broken.get()) {
                return;
            }
            try {
                pipeline.run();
                // After the cup's own tick, inside the same guard: the room reads the cup as this tick left it.
                waitingRoom.tick();
                // Last: the statistics replies a read finished since the last tick. They never touch the cup.
                statistics.tick();
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
            // The statistics stop: the writer drains for at most ten seconds, the count left is logged, and the pool
            // closes after the writer (design D5). Before the graph closes, so the binding is still a live bean.
            try {
                graph.get(StatsBinding.class).shutdown(StatsBinding.SHUTDOWN_DRAIN);
            } catch (RuntimeException exception) {
                // The rest of the stop still runs: the graph and LuckPerms close whatever the statistics did.
                LOGGER.warn("The statistics did not stop cleanly", exception);
            }
            // After the world handles, so a bean's preDestroy hook, should one ever be added, runs
            // once nothing is still reading a world through it.
            graph.close();
            // Last: disables LuckPerms and with it the H2 database, before the JVM runs its own hooks (ADR-0024).
            LuckPermsBootstrap.stop();
        });
    }
}
