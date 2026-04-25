package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.server.scoring.ScoringStrategy;

/**
 * Holds the {@link ScoringStrategy} for the running game so any ECS system or
 * phase can resolve it from the game entity instead of being injected through
 * a constructor chain.
 * <p>
 * Attached once by {@link net.elytrarace.server.game.GameOrchestrator} when a
 * cup starts and lives for the duration of the game.
 *
 * @param strategy the active scoring strategy chosen by the game mode
 */
public record ScoringStrategyComponent(ScoringStrategy strategy) implements Component {
}
