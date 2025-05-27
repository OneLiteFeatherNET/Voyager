package net.elytrarace.game.system;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.System;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.game.portal.components.PortalPositionsComponent;

import java.util.List;
import java.util.Set;

public class CollisionSystem implements System {
    @Override
    public Set<Class<? extends Component>> getRequiredComponents() {
        return Set.of(PortalPositionsComponent.class);
    }

    @Override
    public void process(Entity entity, float deltaTime) {
        PortalPositionsComponent component = entity.getComponent(PortalPositionsComponent.class);
        List<LocationDTO> positions = component.positions();
    }
}
