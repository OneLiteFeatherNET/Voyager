package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.game.scoring.MedalBrackets;

/**
 * Holds the medal bracket configuration used to classify a player's completion
 * time into a {@link net.elytrarace.common.game.scoring.MedalTier}.
 * <p>
 * The reference time is no longer stored here — it comes from {@link MapRecordComponent}
 * (populated from DB at map load or set in-session by the first finisher).
 * Attached to the game entity only.
 */
public record BracketConfigComponent(MedalBrackets brackets) implements Component {
}
