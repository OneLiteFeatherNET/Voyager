package net.elytrarace.common.listener;

import org.bukkit.event.Cancellable;

/**
    * A listener that can cancel events.
 */
public interface CancellableListener {

    /**
     * Cancels the event.
     * @param cancellable The event to cancel
     */
    default void cancelEvent(Cancellable cancellable) {
        cancellable.setCancelled(true);
    }
}
