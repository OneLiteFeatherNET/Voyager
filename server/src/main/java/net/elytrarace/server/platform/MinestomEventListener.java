package net.elytrarace.server.platform;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;

/**
 * Contract for Minestom event listeners used with {@link MinestomEventRegistrar}.
 * <p>
 * Implementations provide an {@link EventNode} containing all their event bindings.
 * The registrar attaches/detaches this node from the event tree on register/unregister.
 *
 * <p>Example usage:
 * {@snippet :
 *     public final class CollisionListener implements MinestomEventListener {
 *
 *         private final EventNode<Event> node = EventNode.all("collision-listener")
 *                 .addListener(PlayerMoveEvent.class, this::onPlayerMove);
 *
 *         @Override
 *         public EventNode<? extends Event> eventNode() {
 *             return node;
 *         }
 *
 *         private void onPlayerMove(PlayerMoveEvent event) {
 *             // handle collision logic
 *         }
 *     }
 * }
 */
public interface MinestomEventListener {

    /**
     * Returns the event node containing this listener's event bindings.
     *
     * @return the event node for this listener
     */
    EventNode<? extends Event> eventNode();
}
