package net.elytrarace.game.util;

import net.elytrarace.game.ElytraRace;

public class PluginInstanceHolder {

    private volatile static ElytraRace pluginInstance;

    public static synchronized ElytraRace getPluginInstance() {
        return pluginInstance;
    }

    public static synchronized void setPluginInstance(ElytraRace pluginInstance) {
        PluginInstanceHolder.pluginInstance = pluginInstance;
    }

}
