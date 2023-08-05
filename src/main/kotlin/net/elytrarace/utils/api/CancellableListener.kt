package net.elytrarace.utils.api

import org.bukkit.event.Cancellable

interface CancellableListener {

    /**
     * Cancel the give event
     */
    fun cancelling(cancellable: Cancellable) {
        cancellable.isCancelled = true
    }
}