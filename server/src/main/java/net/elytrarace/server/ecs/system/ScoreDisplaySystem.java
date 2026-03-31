package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;

import java.util.Set;

/**
 * Updates each player's actionbar HUD with their current speed and score.
 * <p>
 * Only updates while the player is actively flying to avoid spamming the
 * actionbar during lobby or end phases.
 */
public class ScoreDisplaySystem implements net.elytrarace.common.ecs.System {

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

        Player player = playerRef.getPlayer();
        double speedBps = flight.getSpeedBlocksPerSecond();

        player.sendActionBar(
                net.kyori.adventure.text.Component.text("Speed: ", NamedTextColor.WHITE)
                        .append(net.kyori.adventure.text.Component.text(
                                String.format("%.1f", speedBps), NamedTextColor.WHITE))
                        .append(net.kyori.adventure.text.Component.text(
                                " m/s | Points: ", NamedTextColor.WHITE))
                        .append(net.kyori.adventure.text.Component.text(
                                score.getTotal(), NamedTextColor.WHITE)));
    }
}
