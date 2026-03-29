package net.elytrarace.server.world;

import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.anvil.AnvilLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link MapInstanceService} implementation that uses Minestom's {@link AnvilLoader}
 * to load Anvil world directories into {@link InstanceContainer} instances.
 */
public final class AnvilMapInstanceService implements MapInstanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnvilMapInstanceService.class);

    private final InstanceManager instanceManager;
    private final Set<InstanceContainer> loadedInstances = ConcurrentHashMap.newKeySet();

    public AnvilMapInstanceService(InstanceManager instanceManager) {
        this.instanceManager = Objects.requireNonNull(instanceManager, "instanceManager must not be null");
    }

    @Override
    public CompletableFuture<InstanceContainer> loadMap(String mapName, Path worldDirectory) {
        Objects.requireNonNull(mapName, "mapName must not be null");
        Objects.requireNonNull(worldDirectory, "worldDirectory must not be null");

        return CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Loading map '{}' from {}", mapName, worldDirectory);

            var instance = instanceManager.createInstanceContainer();
            instance.setChunkLoader(new AnvilLoader(worldDirectory));

            loadedInstances.add(instance);
            LOGGER.info("Map '{}' loaded successfully (instance {})", mapName, instance.getUuid());
            return instance;
        });
    }

    @Override
    public void unloadMap(InstanceContainer instance) {
        Objects.requireNonNull(instance, "instance must not be null");

        if (!loadedInstances.remove(instance)) {
            LOGGER.warn("Attempted to unload an instance that is not managed by this service: {}", instance.getUuid());
            return;
        }

        LOGGER.info("Unloading map instance {}", instance.getUuid());
        instanceManager.unregisterInstance(instance);
    }

    @Override
    public Collection<InstanceContainer> getLoadedMaps() {
        return Collections.unmodifiableCollection(loadedInstances);
    }
}
