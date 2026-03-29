package net.elytrarace.server;

import net.elytrarace.server.player.PlayerEventHandler;
import net.elytrarace.server.player.PlayerService;
import net.elytrarace.server.player.PlayerServiceImpl;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public VoyagerServer() {
        this.server = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        this.lobbyInstance = instanceManager.createInstanceContainer();
        this.lobbyInstance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));

        this.playerService = new PlayerServiceImpl(lobbyInstance);
        this.playerEventHandler = new PlayerEventHandler(playerService, lobbyInstance);
        this.playerEventHandler.register();
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
