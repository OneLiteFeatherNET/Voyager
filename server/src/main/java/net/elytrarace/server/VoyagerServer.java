package net.elytrarace.server;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.language.LanguageService;
import net.elytrarace.common.map.MapService;
import net.elytrarace.server.command.DevStartCommand;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.CupLoader;
import net.elytrarace.server.game.GameOrchestrator;
import net.elytrarace.server.player.PlayerEventHandler;
import net.elytrarace.server.player.PlayerService;
import net.elytrarace.server.player.PlayerServiceImpl;
import net.elytrarace.server.world.AnvilMapInstanceService;
import net.elytrarace.server.world.MapInstanceService;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Voyager standalone Minestom server entry point.
 * Manages the server lifecycle: initialization, data loading, and startup.
 *
 * <p>Directory layout (relative to working directory):
 * <pre>
 * run/
 *   data/
 *     cups/cups.json
 *     maps/{worldName}/map.json
 *     maps/{worldName}/portals.json
 *   worlds/
 *     {worldName}/   ← Anvil world directories from the Setup Server
 * </pre>
 *
 * Override the defaults via system properties:
 * <ul>
 *   <li>{@code -DVOYAGER_DATA_PATH=...}  — path to the data directory (default: {@code run/data})</li>
 *   <li>{@code -DVOYAGER_WORLDS_PATH=...} — path to the worlds directory (default: {@code run/worlds})</li>
 * </ul>
 */
public final class VoyagerServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoyagerServer.class);
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final int DEFAULT_PORT = 25565;

    private final MinecraftServer server;
    private final InstanceContainer lobbyInstance;
    private final PlayerService playerService;
    private final PlayerEventHandler playerEventHandler;
    private final MapInstanceService mapInstanceService;
    private final GameOrchestrator gameOrchestrator;
    private final CupLoader cupLoader;

    public VoyagerServer() {
        this(
            Path.of(System.getProperty("VOYAGER_DATA_PATH", "run/data")),
            Path.of(System.getProperty("VOYAGER_WORLDS_PATH", "run/worlds"))
        );
    }

    public VoyagerServer(Path dataPath, Path worldsPath) {
        System.setProperty("minestom.chunk-view-distance", "32");
        System.setProperty("minestom.entity-view-distance", "32");
        this.server = MinecraftServer.init();

        LanguageService.create("elytrarace", Key.key("voyager", "lang"), dataPath)
                .loadLanguage()
                .join();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        this.lobbyInstance = instanceManager.createInstanceContainer();
        this.lobbyInstance.setChunkSupplier(LightingChunk::new);
        this.lobbyInstance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));

        this.playerService = new PlayerServiceImpl(lobbyInstance);
        this.playerEventHandler = new PlayerEventHandler(playerService, lobbyInstance);
        this.playerEventHandler.register();

        this.mapInstanceService = new AnvilMapInstanceService(instanceManager);
        this.gameOrchestrator = new GameOrchestrator(playerService, mapInstanceService, playerEventHandler);

        // Wire the ECS entity manager into the event handler for firework boost support
        this.playerEventHandler.setEntityManager(gameOrchestrator.getEntityManager());

        var cupService = CupService.create(dataPath);
        var mapService = MapService.create(dataPath);
        this.cupLoader = new CupLoader(cupService, mapService, worldsPath);

        LOGGER.info("Data path:   {}", dataPath.toAbsolutePath());
        LOGGER.info("Worlds path: {}", worldsPath.toAbsolutePath());

        if (Boolean.getBoolean("voyager.dev")) {
            MinecraftServer.getCommandManager().register(new DevStartCommand(gameOrchestrator));
            LOGGER.warn("Dev mode active — /dev-start command registered (skips lobby countdown)");
        }
    }

    public void start() {
        start(DEFAULT_HOST, DEFAULT_PORT);
    }

    public void start(String host, int port) {
        LOGGER.info("Starting Voyager server on {}:{}", host, port);
        server.start(host, port);
        LOGGER.info("Voyager server started successfully");

        cupLoader.loadFirstCup().ifPresentOrElse(
            cup -> {
                LOGGER.info("Auto-starting cup '{}'", cup.name());
                gameOrchestrator.startGame(cup);
            },
            () -> LOGGER.warn("No cup loaded — place cups/maps under run/data/ and worlds under run/worlds/")
        );
    }

    public void startGame(CupDefinition cup) {
        gameOrchestrator.startGame(cup);
    }

    public InstanceContainer getLobbyInstance() {
        return lobbyInstance;
    }

    public PlayerService getPlayerService() {
        return playerService;
    }

    public MapInstanceService getMapInstanceService() {
        return mapInstanceService;
    }

    public GameOrchestrator getGameOrchestrator() {
        return gameOrchestrator;
    }

    public CupLoader getCupLoader() {
        return cupLoader;
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid port '{}', using default {}", args[1], DEFAULT_PORT);
            }
        }

        var voyagerServer = new VoyagerServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down Voyager server...");
            MinecraftServer.stopCleanly();
        }));

        voyagerServer.start(host, port);
    }
}
