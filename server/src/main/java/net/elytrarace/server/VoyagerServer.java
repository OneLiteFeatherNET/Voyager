package net.elytrarace.server;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
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

    public VoyagerServer() {
        this.server = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        this.lobbyInstance = instanceManager.createInstanceContainer();
        this.lobbyInstance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.STONE));

        registerEvents();
    }

    private void registerEvents() {
        GlobalEventHandler eventHandler = MinecraftServer.getGlobalEventHandler();

        eventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(lobbyInstance);
            event.getPlayer().setRespawnPoint(new Pos(0, 2, 0));
            event.getPlayer().setGameMode(GameMode.ADVENTURE);
        });
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

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        VoyagerServer voyagerServer = new VoyagerServer();
        voyagerServer.start(host, port);
    }
}
