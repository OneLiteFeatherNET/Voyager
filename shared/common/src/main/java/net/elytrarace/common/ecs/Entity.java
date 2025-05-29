package net.elytrarace.common.ecs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Entity {

    private final UUID id;
    private final Map<Class<? extends Component>, Component> components = new HashMap<>();

    public Entity() {
        this.id = UUID.randomUUID();
    }

    public UUID getId() {
        return id;
    }

    public <T extends Component> Entity addComponent(T component) {
        components.put(component.getClass(), component);
        return this;
    }

    public <T extends Component> T getComponent(Class<T> componentClass) {
        return componentClass.cast(components.get(componentClass));
    }

    public <T extends Component> boolean hasComponent(Class<T> componentClass) {
        return components.containsKey(componentClass);
    }

    public <T extends Component> Entity removeComponent(Class<T> componentClass) {
        components.remove(componentClass);
        return this;
    }
}
