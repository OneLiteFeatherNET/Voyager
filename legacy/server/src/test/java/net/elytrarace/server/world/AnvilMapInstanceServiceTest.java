package net.elytrarace.server.world;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class AnvilMapInstanceServiceTest {

    private AnvilMapInstanceService service;

    @BeforeEach
    void setUp() {
        service = new AnvilMapInstanceService(MinecraftServer.getInstanceManager());
    }

    @Test
    void loadMapCreatesInstance(@TempDir Path worldDir, Env env) {
        InstanceContainer instance = service.loadMap("test-map", worldDir).join();

        assertThat(instance).isNotNull();
        assertThat(service.getLoadedMaps()).containsExactly(instance);
    }

    @Test
    void unloadMapRemovesInstance(@TempDir Path worldDir, Env env) {
        InstanceContainer instance = service.loadMap("test-map", worldDir).join();

        service.unloadMap(instance);

        assertThat(service.getLoadedMaps()).isEmpty();
    }

    @Test
    void multipleMapsConcurrentlyLoaded(@TempDir Path worldDir1, Env env) {
        InstanceContainer instance1 = service.loadMap("map-alpha", worldDir1).join();
        InstanceContainer instance2 = service.loadMap("map-beta", worldDir1).join();

        assertThat(service.getLoadedMaps()).hasSize(2);
        assertThat(service.getLoadedMaps()).containsExactlyInAnyOrder(instance1, instance2);

        service.unloadMap(instance1);

        assertThat(service.getLoadedMaps()).containsExactly(instance2);
    }
}
