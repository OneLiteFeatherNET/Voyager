package net.elytrarace.api.phase;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 14.01.2020 08:51
 */
public abstract class GamePhase extends Phase {

    private final JavaPlugin plugin;
    private List<Listener> phaseListeners;

    public GamePhase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public GamePhase(String name, JavaPlugin plugin) {
        super(name);
        this.plugin = plugin;
    }

    @Override
    public void start() {
        super.start();

        if (this instanceof Listener)
            Bukkit.getPluginManager().registerEvents((Listener) this, plugin);
        if (phaseListeners != null && !phaseListeners.isEmpty())
            phaseListeners.forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, plugin));
    }

    @Override
    public void finish() {
        if (this instanceof Listener)
            HandlerList.unregisterAll((Listener) this);
        if (phaseListeners != null)
            phaseListeners.forEach(HandlerList::unregisterAll);

        super.finish();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void addPhaseListener(Listener listener) {
        if (phaseListeners == null)
            phaseListeners = new ArrayList<>(3);

        phaseListeners.add(listener);
    }

    public void removePhaseListener(Listener listener) {
        if (phaseListeners != null)
            phaseListeners.remove(listener);
    }
}