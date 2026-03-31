package net.elytrarace.server.ecs.component;

import net.elytrarace.common.ecs.Component;
import net.elytrarace.server.cup.MapDefinition;
import net.minestom.server.instance.InstanceContainer;

/**
 * Attached to the game entity to reference the currently active map and its server instance.
 */
public class ActiveMapComponent implements Component {

    private MapDefinition currentMap;
    private InstanceContainer mapInstance;

    public MapDefinition getCurrentMap() {
        return currentMap;
    }

    public void setCurrentMap(MapDefinition currentMap) {
        this.currentMap = currentMap;
    }

    public InstanceContainer getMapInstance() {
        return mapInstance;
    }

    public void setMapInstance(InstanceContainer mapInstance) {
        this.mapInstance = mapInstance;
    }
}
