package net.elytrarace.server.player;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.physics.ElytraPhysics;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
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
     * Sets the ECS entity manager used to look up player flight state for firework boosts.
     * Must be called before {@link #register()} or the firework boost handler will be inactive.
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

    private void onUseItem(PlayerUseItemEvent event) {
        Player player = event.getPlayer();

        // Only handle firework rockets
        if (event.getItemStack().material() != Material.FIREWORK_ROCKET) {
            return;
        }

        if (entityManager == null) {
            return;
        }

        // Find the player's ECS entity and check if they are flying elytra
        ElytraFlightComponent flight = findFlightComponent(player);
        if (flight == null || !flight.isFlying()) {
            return;
        }

        // Apply the firework boost as a direct forward impulse.
        // We do NOT use flight.getVelocity() because the server-side velocity estimate
        // may lag behind the client's actual speed. Instead we compute a pure look-direction
        // impulse and add it on top of the client's current movement.
        Pos pos = player.getPosition();
        double pitchRad = Math.toRadians(pos.pitch());
        double yawRad   = Math.toRadians(pos.yaw());
        double cosP = Math.cos(pitchRad);
        Vec lookDir = new Vec(
            Math.sin(-yawRad - Math.PI) * (-cosP),
            -Math.sin(pitchRad),
            Math.cos(-yawRad - Math.PI) * (-cosP)
        );
        // Use server-estimated velocity as the base; fall back to look direction at
        // a reasonable glide speed so the impulse is always additive.
        Vec base = flight.getVelocity().lengthSquared() > 0.01
            ? flight.getVelocity()
            : lookDir.mul(0.6);  // ~12 blocks/second baseline if no estimate available
        Vec boostedVelocity = ElytraPhysics.applyFireworkBoost(base, pos.pitch(), pos.yaw());
        flight.setVelocity(boostedVelocity);
        player.setVelocity(boostedVelocity);

        // Consume one rocket from the stack
        var stack = event.getItemStack();
        if (stack.amount() > 1) {
            player.setItemInHand(event.getHand(), stack.withAmount(stack.amount() - 1));
        } else {
            player.setItemInHand(event.getHand(), net.minestom.server.item.ItemStack.AIR);
        }

        LOGGER.debug("Firework boost applied to player {}", player.getUsername());
    }

    /**
     * Finds the {@link ElytraFlightComponent} for the given player from the ECS entity manager.
     */
    private @Nullable ElytraFlightComponent findFlightComponent(Player player) {
        if (entityManager == null) {
            return null;
        }
        for (Entity entity : entityManager.getEntities()) {
            if (!entity.hasComponent(PlayerRefComponent.class)) {
                continue;
            }
            var ref = entity.getComponent(PlayerRefComponent.class);
            if (ref.getPlayerId().equals(player.getUuid())) {
                return entity.getComponent(ElytraFlightComponent.class);
            }
        }
        return null;
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
