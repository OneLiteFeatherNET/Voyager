package net.elytrarace.server.player;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.FireworkBoostComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Registers Minestom player lifecycle events and delegates to {@link PlayerService}.
 * <p>
 * Uses an {@link EventNode} scoped to {@code "player-events"} that is attached
 * to the global event handler. This keeps player-related event logic isolated
 * and easy to remove or replace.
 * <p>
 * Firework boost logic lives entirely in the ECS via {@link FireworkBoostComponent}
 * and {@code FireworkBoostSystem}. This handler only signals intent by setting the
 * component flag — all cooldown tracking and impulse application happen in the tick loop.
 */
public final class PlayerEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerEventHandler.class);
    private static final Pos DEFAULT_SPAWN = new Pos(0, 2, 0);

    private final PlayerService playerService;
    private final InstanceContainer lobbyInstance;
    private final EventNode<Event> eventNode;
    private @Nullable EntityManager entityManager;

    /**
     * Creates a new event handler that delegates player events to the given service.
     *
     * @param playerService the player service to delegate to
     * @param lobbyInstance the lobby instance used as the spawn instance for joining players
     */
    public PlayerEventHandler(PlayerService playerService, InstanceContainer lobbyInstance) {
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.lobbyInstance = Objects.requireNonNull(lobbyInstance, "lobbyInstance must not be null");
        this.eventNode = EventNode.all("player-events");
    }

    /**
     * Sets the ECS entity manager used to look up player entities on firework use.
     * Must be called before {@link #register()} or firework boost signals will be dropped.
     *
     * @param entityManager the ECS entity manager
     */
    public void setEntityManager(@Nullable EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Registers all player-related event listeners and attaches the event node
     * to the global event handler.
     */
    public void register() {
        eventNode.addListener(AsyncPlayerConfigurationEvent.class, this::onConfiguration);
        eventNode.addListener(PlayerDisconnectEvent.class, this::onDisconnect);
        eventNode.addListener(PlayerSpawnEvent.class, this::onSpawn);
        eventNode.addListener(PlayerUseItemEvent.class, this::onUseItem);

        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        LOGGER.info("Player event handlers registered");
    }

    /**
     * Removes the event node from the global event handler, effectively
     * unregistering all player-related listeners.
     */
    public void unregister() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        LOGGER.info("Player event handlers unregistered");
    }

    private void onConfiguration(AsyncPlayerConfigurationEvent event) {
        event.setSpawningInstance(lobbyInstance);
        event.getPlayer().setRespawnPoint(DEFAULT_SPAWN);
        playerService.onPlayerJoin(event.getPlayer());
    }

    private void onDisconnect(PlayerDisconnectEvent event) {
        playerService.onPlayerLeave(event.getPlayer());
    }

    private void onSpawn(PlayerSpawnEvent event) {
        if (event.isFirstSpawn()) {
            event.getPlayer().setGameMode(GameMode.ADVENTURE);
        }
    }

    /**
     * Signals a boost request to the ECS when the player uses a firework rocket.
     * <p>
     * The request is stored atomically on {@link FireworkBoostComponent} and consumed
     * by {@code FireworkBoostSystem} on the next tick. Cooldown enforcement and impulse
     * application are fully handled inside the ECS.
     */
    private void onUseItem(PlayerUseItemEvent event) {
        if (event.getItemStack().material() != Material.FIREWORK_ROCKET) {
            return;
        }
        if (entityManager == null) {
            return;
        }
        Player player = event.getPlayer();
        for (Entity entity : entityManager.getEntities()) {
            if (!entity.hasComponent(PlayerRefComponent.class)) {
                continue;
            }
            if (!entity.getComponent(PlayerRefComponent.class).getPlayerId().equals(player.getUuid())) {
                continue;
            }
            var boostComp = entity.getComponent(FireworkBoostComponent.class);
            if (boostComp != null) {
                boostComp.requestBoost();
            }
            break;
        }
    }

    /**
     * Returns the event node managed by this handler (mainly useful for testing).
     *
     * @return the event node
     */
    public EventNode<Event> getEventNode() {
        return eventNode;
    }
}
