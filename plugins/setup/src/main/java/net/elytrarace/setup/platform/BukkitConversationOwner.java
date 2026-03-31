package net.elytrarace.setup.platform;

import net.elytrarace.api.conversation.ConversationOwner;
import net.elytrarace.api.conversation.ConversationScheduler;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Paper/Bukkit implementation of ConversationOwner that wraps a Bukkit Plugin.
 */
public class BukkitConversationOwner implements ConversationOwner {

    private final Plugin plugin;
    private final ConversationScheduler scheduler;

    public BukkitConversationOwner(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = new BukkitConversationScheduler(plugin);
    }

    @Override
    @NotNull
    public String getName() {
        return plugin.getDescription().getName();
    }

    @Override
    @NotNull
    public ConversationScheduler getScheduler() {
        return scheduler;
    }

    /**
     * Gets the underlying Bukkit Plugin.
     */
    @NotNull
    public Plugin getPlugin() {
        return plugin;
    }

    private static class BukkitConversationScheduler implements ConversationScheduler {
        private final Plugin plugin;

        BukkitConversationScheduler(Plugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public int scheduleDelayed(@NotNull Runnable task, long delayTicks) {
            return plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, task, delayTicks);
        }

        @Override
        public void cancelTask(int taskId) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }
}
