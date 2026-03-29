package net.elytrarace.server;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class VoyagerServerTest {

    @Test
    void serverProcessIsRunning(Env env) {
        assertThat(MinecraftServer.process()).isNotNull();
    }

    @Test
    void canCreateGameInstance(Env env) {
        var instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instance = instanceManager.createInstanceContainer();
        assertThat(instance).isNotNull();
        assertThat(instanceManager.getInstances()).contains(instance);
    }
}
