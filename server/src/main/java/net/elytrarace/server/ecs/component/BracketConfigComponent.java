package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.game.scoring.MedalBrackets;

import java.time.Duration;

/**
 * Holds the medal bracket configuration and the reference duration used to
 * classify a player's completion time into a {@link net.elytrarace.common.game.scoring.MedalTier}.
 * <p>
 * Attached to the game entity only. Replaced by {@code GameOrchestrator.loadNextMap()}
 * on each new map using {@link MedalBrackets#DEFAULT} and the map's
 * {@code referenceDurationMs()}.
 */
public record BracketConfigComponent(MedalBrackets brackets, Duration reference) implements Component {
}
