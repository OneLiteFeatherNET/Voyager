package net.elytrarace.game.portal.components;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.map.model.LocationDTO;

import java.util.List;

public record CenterPositionComponent(List<LocationDTO> positions) implements Component {
}
