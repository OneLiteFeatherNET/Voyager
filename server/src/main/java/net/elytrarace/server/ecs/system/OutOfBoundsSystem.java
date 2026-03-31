package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.player.PlayerService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resets a player to the current map's spawn position when they:
 * <ul>
 *   <li>fly below the world floor (Y &lt; {@value #MIN_Y})</li>
 *   <li>fly above the world ceiling (Y &gt; {@value #MAX_Y})</li>
 *   <li>land on the ground after having been airborne for at least
 *       {@value #MIN_AIR_TICKS_BEFORE_LAND_RESET} ticks</li>
 * </ul>
 *
 * <p>A short cooldown ({@value #RESET_COOLDOWN_TICKS} ticks) is applied after each
 * reset so the landing check does not trigger again while the player is standing
 * at the spawn position before re-activating their elytra.
 */
public class OutOfBoundsSystem implements net.elytrarace.common.ecs.System {

    /** Minecraft void floor — reset when the player falls below this Y. */
    static final double MIN_Y = -64.0;

    /** Minecraft world height ceiling — reset when the player flies above this Y. */
    static final double MAX_Y = 320.0;

    /**
     * Minimum consecutive airborne ticks required before a landing triggers a reset.
     * Prevents immediate re-reset when the player is standing at spawn after a teleport.
     */
    static final int MIN_AIR_TICKS_BEFORE_LAND_RESET = 20;

    /**
     * Ticks after a reset during which ground-landing checks are suppressed (2 s at 20 TPS).
     * Out-of-bounds checks are always active.
     */
    static final int RESET_COOLDOWN_TICKS = 40;

    private final EntityManager entityManager;
    private final PlayerService playerService;

    /** Counts consecutive ticks each player has been airborne since the last landing/reset. */
    private final Map<UUID, Integer> airTicks = new ConcurrentHashMap<>();

    /** Remaining ticks of reset cooldown per player. */
    private final Map<UUID, Integer> resetCooldown = new ConcurrentHashMap<>();

    public OutOfBoundsSystem(EntityManager entityManager, PlayerService playerService) {
        this.entityManager = entityManager;
        this.playerService = playerService;
    }

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PlayerRefComponent.class, ElytraFlightComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var flight = entity.getComponent(ElytraFlightComponent.class);
        if (!flight.isFlying()) {
            return;
        }

        var playerRef = entity.getComponent(PlayerRefComponent.class);
        Player player = playerRef.getPlayer();
        UUID uuid = player.getUuid();

        double y = player.getPosition().y();

        // Out-of-bounds is always checked — no cooldown guard
        if (y < MIN_Y || y > MAX_Y) {
            MapDefinition map = findActiveMap();
            if (map != null) {
                resetPlayer(player, flight, map.spawnPos(), uuid);
            }
            return;
        }

        // Drain cooldown — skip landing check while cooling down
        Integer remaining = resetCooldown.get(uuid);
        if (remaining != null) {
            if (remaining <= 1) {
                resetCooldown.remove(uuid);
            } else {
                resetCooldown.put(uuid, remaining - 1);
            }
            return;
        }

        // Track airborne ticks
        if (player.isOnGround()) {
            int air = airTicks.getOrDefault(uuid, 0);
            airTicks.remove(uuid);

            if (air >= MIN_AIR_TICKS_BEFORE_LAND_RESET) {
                MapDefinition map = findActiveMap();
                if (map != null) {
                    resetPlayer(player, flight, map.spawnPos(), uuid);
                }
            }
        } else {
            airTicks.merge(uuid, 1, Integer::sum);
        }
    }

    private void resetPlayer(Player player, ElytraFlightComponent flight, Pos spawnPos, UUID uuid) {
        resetCooldown.put(uuid, RESET_COOLDOWN_TICKS);
        airTicks.remove(uuid);

        flight.setVelocity(Vec.ZERO);
        flight.setPreviousPosition(null);

        player.teleport(spawnPos).thenRun(() -> playerService.equipForRace(player));
    }

    private MapDefinition findActiveMap() {
        for (Entity entity : entityManager.getEntities()) {
            if (entity.hasComponent(ActiveMapComponent.class)) {
                return entity.getComponent(ActiveMapComponent.class).getCurrentMap();
            }
        }
        return null;
    }
}
