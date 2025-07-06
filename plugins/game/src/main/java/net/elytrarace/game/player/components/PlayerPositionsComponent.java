package net.elytrarace.game.player.components;

import net.elytrarace.common.ecs.Component;
import org.apache.commons.geometry.euclidean.threed.Vector3D;

import java.util.UUID;

/**
 * Component that stores a player's last positions for collision detection.
 */
public record PlayerPositionsComponent(UUID playerId, Vector3D[] lastPositions) implements Component {
}