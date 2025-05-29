package net.elytrarace.common.ecs;

import java.util.Set;

public interface System {
    Set<Class<? extends Component>> getRequiredComponents();
    void process(Entity entity, float deltaTime);
}
