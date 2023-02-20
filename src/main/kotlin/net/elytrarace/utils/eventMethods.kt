package net.elytrarace.utils

import org.bukkit.event.Cancellable

fun cancelling(cancellable: Cancellable) {
    cancellable.isCancelled = true
}