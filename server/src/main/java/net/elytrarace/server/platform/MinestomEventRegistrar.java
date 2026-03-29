package net.elytrarace.server.platform;

import net.elytrarace.api.phase.EventRegistrar;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.Event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minestom implementation of {@link EventRegistrar}.
 * <p>
 * Minestom uses an {@link EventNode} tree instead of annotation-based listener registration.
 * Listeners passed to this registrar must implement {@link MinestomEventListener} to provide
 * their event bindings as a child {@link EventNode}. Each listener gets its own isolated node
 * that is attached to (and detached from) the parent node on register/unregister.
 */
public final class MinestomEventRegistrar implements EventRegistrar {

    private final EventNode<Event> parentNode;
    private final Map<Object, EventNode<? extends Event>> registeredNodes = new ConcurrentHashMap<>();

    /**
     * Creates a new registrar that attaches listener nodes to the given parent.
     *
     * @param parentNode the parent event node (typically the global event handler)
     */
    public MinestomEventRegistrar(EventNode<Event> parentNode) {
        this.parentNode = parentNode;
    }

    @Override
    public void registerListener(Object listener) {
        if (listener instanceof MinestomEventListener minestomListener) {
            EventNode<? extends Event> childNode = minestomListener.eventNode();
            registeredNodes.put(listener, childNode);
            parentNode.addChild(childNode);
        }
    }

    @Override
    public void unregisterListener(Object listener) {
        EventNode<? extends Event> childNode = registeredNodes.remove(listener);
        if (childNode != null) {
            parentNode.removeChild(childNode);
        }
    }
}
