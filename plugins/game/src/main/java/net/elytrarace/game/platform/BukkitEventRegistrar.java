package net.elytrarace.game.platform;

import net.elytrarace.api.phase.EventRegistrar;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit/Paper implementation of {@link EventRegistrar}.
 */
public final class BukkitEventRegistrar implements EventRegistrar {

    private final JavaPlugin plugin;

    public BukkitEventRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerListener(Object listener) {
        if (listener instanceof Listener bukkitListener) {
            Bukkit.getPluginManager().registerEvents(bukkitListener, plugin);
        }
    }

    @Override
    public void unregisterListener(Object listener) {
        if (listener instanceof Listener bukkitListener) {
            HandlerList.unregisterAll(bukkitListener);
        }
    }
}
