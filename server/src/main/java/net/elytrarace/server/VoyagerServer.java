package net.elytrarace.server;

import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.game.GameOrchestrator;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.player.PlayerEventHandler;
import net.elytrarace.server.player.PlayerService;
import net.elytrarace.server.player.PlayerServiceImpl;
import net.elytrarace.server.world.AnvilMapInstanceService;
import net.elytrarace.server.world.MapInstanceService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Voyager standalone Minestom server entry point.
 * Manages the server lifecycle: initialization, instance creation, and shutdown.
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

    public VoyagerServer() {
        this.server = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        this.lobbyInstance = instanceManager.createInstanceContainer();
        this.lobbyInstance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));

        this.playerService = new PlayerServiceImpl(lobbyInstance);
        this.playerEventHandler = new PlayerEventHandler(playerService, lobbyInstance);
        this.playerEventHandler.register();

        this.mapInstanceService = new AnvilMapInstanceService(instanceManager);
        this.gameOrchestrator = new GameOrchestrator(playerService, mapInstanceService);
    }

    public void start() {
        start(DEFAULT_HOST, DEFAULT_PORT);
    }

    public void start(String host, int port) {
        LOGGER.info("Starting Voyager server on {}:{}", host, port);
        server.start(host, port);
        LOGGER.info("Voyager server started successfully");
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

    /**
     * Starts a game session for the given cup definition.
     * Delegates to the {@link GameOrchestrator} to wire up all subsystems.
     *
     * @param cup the cup definition to start
     */
    public void startGame(CupDefinition cup) {
        gameOrchestrator.startGame(cup);
    }

    /**
     * Creates a demo cup with three placeholder maps for testing purposes.
     * The maps have no rings and use temporary world directories.
     *
     * @return a demo cup definition
     */
    public static CupDefinition createDemoCup() {
        var ring = new Ring(new Vec(0, 50, 50), new Vec(0, 0, 1), 5.0, 10);
        var map1 = new MapDefinition("Demo Map 1", Path.of("/tmp/demo-map-1"), List.of(ring), new Pos(0, 60, 0));
        var map2 = new MapDefinition("Demo Map 2", Path.of("/tmp/demo-map-2"), List.of(ring), new Pos(0, 60, 0));
        var map3 = new MapDefinition("Demo Map 3", Path.of("/tmp/demo-map-3"), List.of(ring), new Pos(0, 60, 0));
        return new CupDefinition("Demo Cup", List.of(map1, map2, map3));
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

        VoyagerServer voyagerServer = new VoyagerServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down Voyager server...");
            MinecraftServer.stopCleanly();
        }));

        voyagerServer.start(host, port);
    }
}
