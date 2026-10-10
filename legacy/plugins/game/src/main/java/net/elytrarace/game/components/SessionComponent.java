package net.elytrarace.game.components;

import net.elytrarace.common.ecs.Component;

import java.util.UUID;

/**
 * Component that stores the session ID.
 * This is part of the flattened architecture, replacing part of the GameStateComponent.
 */
public record SessionComponent(UUID sessionId) implements Component {
    
    /**
     * Creates a new SessionComponent with a random session ID.
     */
    public static SessionComponent create() {
        return new SessionComponent(UUID.randomUUID());
    }
    
    /**
     * Creates a new SessionComponent with the given session ID.
     */
    public static SessionComponent create(UUID sessionId) {
        return new SessionComponent(sessionId);
    }
}