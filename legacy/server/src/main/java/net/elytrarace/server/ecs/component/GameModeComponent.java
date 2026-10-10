package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.game.mode.GameMode;

public record GameModeComponent(GameMode mode) implements Component {
}
