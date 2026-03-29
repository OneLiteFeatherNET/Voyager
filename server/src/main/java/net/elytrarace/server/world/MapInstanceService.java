package net.elytrarace.server.world;

import net.minestom.server.instance.InstanceContainer;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Service for loading and managing Anvil world instances as map arenas.
 */
public interface MapInstanceService {

    /**
     * Loads an Anvil world from the given directory into a new {@link InstanceContainer}.
     *
     * @param mapName        a logical name for the map (used for logging and identification)
     * @param worldDirectory the path to the Anvil world directory
     * @return a future that completes with the created instance
     */
    CompletableFuture<InstanceContainer> loadMap(String mapName, Path worldDirectory);

    /**
     * Unloads and unregisters the given map instance, releasing all associated resources.
     *
     * @param instance the instance to unload
     */
    void unloadMap(InstanceContainer instance);

    /**
     * Returns an unmodifiable view of all currently loaded map instances.
     *
     * @return collection of loaded instances
     */
    Collection<InstanceContainer> getLoadedMaps();
}
