package net.elytrarace.api.phase;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:40
 */

public abstract class TickedPhase extends GamePhase {

    public TickedPhase(JavaPlugin plugin) {
        super(plugin);
    }

    public TickedPhase(String name, JavaPlugin javaPlugin) {
        super(name, javaPlugin);
    }

    public abstract void onUpdate();
}