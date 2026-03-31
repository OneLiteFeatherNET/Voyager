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
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
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
    private final Map<UUID, Long> lastBoostTime = new ConcurrentHashMap<>();
    private @Nullable EntityManager entityManager;

    /** Active boost configuration — swapped out when a new map loads. */
    private volatile BoostConfig boostConfig = BoostConfig.DEFAULT;

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
        lastBoostTime.remove(event.getPlayer().getUuid());
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

        if (entityManager == null) {
            return;
        }

        ElytraFlightComponent flight = findFlightComponent(player);
        if (flight == null || !flight.isFlying()) {
            return;
        }

        // Cooldown guard
        long now = System.currentTimeMillis();
        Long last = lastBoostTime.get(player.getUuid());
        if (last != null && (now - last) < boostConfig.cooldownMs()) {
            return;
        }
        lastBoostTime.put(player.getUuid(), now);

        // Consume one rocket
        var stack = event.getItemStack();
        if (stack.amount() > 1) {
            player.setItemInHand(event.getHand(), stack.withAmount(stack.amount() - 1));
        } else {
            player.setItemInHand(event.getHand(), ItemStack.AIR);
        }

        applyBoost(player, flight);
    }

    /**
     * Applies a firework boost to the player in their full look direction (yaw + pitch).
     * <p>
     * The boost direction is computed from both yaw and pitch using vanilla Minecraft
     * look-direction math, so players control exactly where the boost pushes them:
     * looking up boosts upward, looking level boosts forward, looking down boosts downward.
     * <p>
     * The boost is sustained for {@link BoostConfig#durationTicks()} ticks so the client's
     * own elytra physics can "feel" it over several frames rather than a single packet that
     * the physics loop might partially override.
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
        Vec boostPerTick = new Vec(lookX, lookY, lookZ).mul(cfg.speedBlocksPerTick());
        // Minestom setVelocity() expects blocks/second — multiply by 20
        Vec boostPerSecond = boostPerTick.mul(20.0);

        // Sustain boost for durationTicks so the client's physics can "feel" it.
        // Uses repeat() + cancel() since buildTask(Runnable) does not accept a Supplier<TaskSchedule>.
        var remaining = new java.util.concurrent.atomic.AtomicInteger(cfg.durationTicks());
        var taskRef   = new Task[1];
        taskRef[0] = MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!player.isOnline() || remaining.decrementAndGet() < 0) {
                if (taskRef[0] != null) taskRef[0].cancel();
                return;
            }
            flight.setVelocity(boostPerTick);
            player.setVelocity(boostPerSecond);
        }).repeat(TaskSchedule.nextTick()).schedule();

        LOGGER.debug("Boost applied to {} — {} b/t along look direction for {} ticks",
                player.getUsername(), cfg.speedBlocksPerTick(), cfg.durationTicks());
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
