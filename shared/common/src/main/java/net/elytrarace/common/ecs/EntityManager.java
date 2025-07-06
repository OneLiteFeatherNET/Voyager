package net.elytrarace.common.ecs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EntityManager {
    private final Set<Entity> entities = new HashSet<>();
    private final List<System> systems = new ArrayList<>();

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    public void addSystem(System system) {
        systems.add(system);
    }

    public void update(float deltaTime) {
        for (System system : systems) {
            Set<Class<? extends Component>> requiredComponents = system.getRequiredComponents();
            entities.stream()
                    .filter(entity -> hasAllComponents(entity, requiredComponents))
                    .forEach(entity -> system.process(entity, deltaTime));
        }
    }

    private static boolean hasAllComponents(Entity entity, Set<Class<? extends Component>> componentClasses) {
        return componentClasses.stream().allMatch(entity::hasComponent);
    }
}
