package net.elytrarace.setup.platform;

import net.elytrarace.api.phase.PhaseScheduler;
import net.elytrarace.api.phase.PhaseTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bukkit/Paper implementation of {@link PhaseScheduler}.
 */
public final class BukkitPhaseScheduler implements PhaseScheduler {

    private final JavaPlugin plugin;

    public BukkitPhaseScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public PhaseTask runRepeating(Runnable task, long intervalTicks, boolean async) {
        BukkitTask bukkitTask;
        if (async) {
            bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, 0L, intervalTicks);
        } else {
            bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, 0L, intervalTicks);
        }
        return bukkitTask::cancel;
    }
}
