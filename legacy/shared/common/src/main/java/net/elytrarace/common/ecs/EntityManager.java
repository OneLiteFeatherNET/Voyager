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

    /**
     * Gets all entities managed by this EntityManager.
     * 
     * @return An unmodifiable set of all entities.
     */
    public Set<Entity> getEntities() {
        return Set.copyOf(entities);
    }

    /**
     * Gets all entities that have the specified component.
     * 
     * @param componentClass The component class to check for.
     * @return A set of entities that have the specified component.
     */
    public <T extends Component> Set<Entity> getEntitiesWithComponent(Class<T> componentClass) {
        return entities.stream()
                .filter(entity -> entity.hasComponent(componentClass))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Gets all systems managed by this EntityManager.
     * 
     * @return An unmodifiable list of all systems.
     */
    public List<System> getSystems() {
        return List.copyOf(systems);
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
