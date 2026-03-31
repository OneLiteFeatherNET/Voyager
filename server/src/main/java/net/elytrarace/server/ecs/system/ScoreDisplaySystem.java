package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Updates each player's actionbar HUD with their current speed and score.
 * <p>
 * Only updates while the player is actively flying to avoid spamming the
 * actionbar during lobby or end phases. Throttled to every 4 ticks (5 Hz)
 * and only re-sends when displayed values change, to prevent visual flickering.
 */
public class ScoreDisplaySystem implements net.elytrarace.common.ecs.System {

    private static final int TICK_INTERVAL = 4;

    private final Map<UUID, Integer> tickCounters = new HashMap<>();
    private final Map<UUID, Long> lastDisplayHash = new HashMap<>();

    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PlayerRefComponent.class, ScoreComponent.class, ElytraFlightComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var score = entity.getComponent(ScoreComponent.class);
        var playerRef = entity.getComponent(PlayerRefComponent.class);

        if (!flight.isFlying()) {
            return;
        }

        UUID entityId = entity.getId();
        int ticks = tickCounters.getOrDefault(entityId, 0) + 1;
        if (ticks < TICK_INTERVAL) {
            tickCounters.put(entityId, ticks);
            return;
        }
        tickCounters.put(entityId, 0);

        Player player = playerRef.getPlayer();
        double speedBps = flight.getSpeedBlocksPerSecond();
        int totalScore = score.getTotal();

        // Only re-send if values changed (speed rounded to 1 decimal + score)
        long speedKey = Math.round(speedBps * 10);
        long displayHash = (speedKey << 32) | (totalScore & 0xFFFFFFFFL);
        Long previous = lastDisplayHash.get(entityId);
        if (previous != null && previous == displayHash) {
            return;
        }
        lastDisplayHash.put(entityId, displayHash);

        player.sendActionBar(
                net.kyori.adventure.text.Component.text("Speed: ", NamedTextColor.WHITE)
                        .append(net.kyori.adventure.text.Component.text(
                                String.format("%.1f", speedBps), NamedTextColor.WHITE))
                        .append(net.kyori.adventure.text.Component.text(
                                " m/s | Points: ", NamedTextColor.WHITE))
                        .append(net.kyori.adventure.text.Component.text(
                                totalScore, NamedTextColor.WHITE)));
    }
}
