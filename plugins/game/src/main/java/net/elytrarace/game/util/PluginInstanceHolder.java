package net.elytrarace.game.util;

import net.elytrarace.common.ecs.EntityManager;
import org.bukkit.plugin.java.JavaPlugin;

public enum PluginInstanceHolder {
    ;

    private static volatile JavaPlugin pluginInstance;
    private static volatile EntityManager entityManager;

    public static synchronized JavaPlugin getPluginInstance() {
        return pluginInstance;
    }

    public static synchronized void setPluginInstance(JavaPlugin pluginInstance) {
        PluginInstanceHolder.pluginInstance = pluginInstance;
    }

    public static synchronized EntityManager getEntityManager() {
        return entityManager;
    }

    public static synchronized void setEntityManager(EntityManager entityManager) {
        PluginInstanceHolder.entityManager = entityManager;
    }
}
