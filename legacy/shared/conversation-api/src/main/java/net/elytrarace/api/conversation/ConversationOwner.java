package net.elytrarace.api.conversation;

import org.jetbrains.annotations.NotNull;

/**
 * Platform-agnostic abstraction for the owner of a conversation (replaces org.bukkit.plugin.Plugin).
 * Implementations provide scheduling and naming capabilities.
 */
public interface ConversationOwner {

    /**
     * Gets the name of this conversation owner (typically the plugin name).
     *
     * @return The owner name.
     */
    @NotNull
    String getName();

    /**
     * Gets the scheduler for this conversation owner.
     *
     * @return The conversation scheduler.
     */
    @NotNull
    ConversationScheduler getScheduler();
}
