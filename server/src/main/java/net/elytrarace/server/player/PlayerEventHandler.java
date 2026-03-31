package net.elytrarace.server.player;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.BoostConfig;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
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
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    /** Tracks per-player cooldown expiry as {@link System#currentTimeMillis()} deadline. */
    private final Map<UUID, Long> boostCooldownExpiry = new ConcurrentHashMap<>();
    private @Nullable EntityManager entityManager;

    /** Active boost configuration — swapped out when a new map loads. */
    private volatile BoostConfig boostConfig = BoostConfig.DEFAULT;

    /**
     * The cooldown group sent to the client via {@link SetCooldownPacket}.
     * Must match the item's registry key so the client greys out the correct hotbar slot.
     */
    private static final String FIREWORK_COOLDOWN_GROUP = Material.FIREWORK_ROCKET.key().asString();

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
     * Updates the active boost configuration. Call this whenever a new map loads
     * so the boost feel can be tuned per-map.
     *
     * @param config the new boost configuration; must not be null
     */
    public void setBoostConfig(BoostConfig config) {
        this.boostConfig = Objects.requireNonNull(config, "boostConfig must not be null");
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
        boostCooldownExpiry.remove(event.getPlayer().getUuid());
        playerService.onPlayerLeave(event.getPlayer());
    }

    private void onSpawn(PlayerSpawnEvent event) {
        if (event.isFirstSpawn()) {
            event.getPlayer().setGameMode(GameMode.ADVENTURE);
        }
    }

    private void onUseItem(PlayerUseItemEvent event) {
        Player player = event.getPlayer();

        if (event.getItemStack().material() != Material.FIREWORK_ROCKET) {
            return;
        }

        // Server-side cooldown guard — the client won't send USE_ITEM while the
        // hotbar slot is greyed out, but defend against race conditions anyway.
        Long expiry = boostCooldownExpiry.get(player.getUuid());
        if (expiry != null && System.currentTimeMillis() < expiry) {
            return;
        }

        if (entityManager == null) {
            return;
        }

        ElytraFlightComponent flight = findFlightComponent(player);
        if (flight == null || !flight.isFlying()) {
            return;
        }

        // Record cooldown expiry and send native item cooldown packet so the
        // firework rocket slot is visually greyed out in the hotbar.
        BoostConfig cfg = this.boostConfig;
        long cooldownMs = cfg.cooldownMs();
        boostCooldownExpiry.put(player.getUuid(), System.currentTimeMillis() + cooldownMs);

        int cooldownTicks = (int) (cooldownMs / 50L);
        player.sendPacket(new SetCooldownPacket(FIREWORK_COOLDOWN_GROUP, cooldownTicks));

        applyBoost(player, flight);
    }

    /**
     * Applies a firework boost to the player in their full look direction (yaw + pitch).
     * <p>
     * The boost direction is computed from both yaw and pitch using vanilla Minecraft
     * look-direction math, so players control exactly where the boost pushes them:
     * looking up boosts upward, looking level boosts forward, looking down boosts downward.
     * <p>
     * The boost is a single one-shot impulse — the client's own elytra physics handle
     * drag, gravity and steering from that point on, so the player retains full
     * directional control immediately after the kick.
     * <p>
     * Note: {@code player.setVelocity()} expects <b>blocks/second</b> in Minestom.
     * All internal calculations use blocks/tick; the final value is multiplied by 20
     * before being passed to Minestom.
     */
    private void applyBoost(Player player, ElytraFlightComponent flight) {
        BoostConfig cfg = this.boostConfig;

        // Boost direction: player's full look direction (yaw + pitch).
        // Minecraft convention: yaw 0 = south (+Z), pitch negative = looking up.
        double yawRad   = Math.toRadians(player.getPosition().yaw());
        double pitchRad = Math.toRadians(player.getPosition().pitch());

        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ =  Math.cos(yawRad) * Math.cos(pitchRad);

        // Scale to configured speed (blocks/tick)
        Vec boostPerTick   = new Vec(lookX, lookY, lookZ).mul(cfg.speedBlocksPerTick());
        // Minestom setVelocity() expects blocks/second — multiply by 20
        Vec boostPerSecond = boostPerTick.mul(20.0);

        // One-shot impulse: set velocity once, let client elytra physics take over
        flight.setVelocity(boostPerTick);
        player.setVelocity(boostPerSecond);

        LOGGER.debug("Boost applied to {} — {} b/t along look direction",
                player.getUsername(), cfg.speedBlocksPerTick());
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
