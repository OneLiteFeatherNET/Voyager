package net.elytrarace.voyager.setup.spike;

import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.exception.UnknownWorldException;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spike 1.3: the smallest content of a void world that MapInstances accepts. Not part of the test task.
 */
@EnvTest
class VoidWorldSpikeTest {

    @TempDir
    Path worlds;

    @Test
    void aWorldWithOneEmptyRegionFileIsAcceptedByTheFalcoLoader(Env env) throws IOException {
        Path region = Files.createDirectories(worlds.resolve("void").resolve("region"));
        Files.write(region.resolve("r.0.0.mca"), new byte[8192]);

        try (MapInstances instances = new MapInstances(env.process().instance(), worlds)) {
            Instance instance = instances.forWorld("void");
            assertThat(instance).isNotNull();
            // isSound() also requires a loaded chunk, which an empty void has until something reads it.
            // Reading every chunk of the region must not throw, and the report must be produced.
            assertThatCode(() -> instances.readEveryChunk("void")).doesNotThrowAnyException();
            assertThat(instances.healthOf("void").describe()).isNotBlank();
        }
    }

    @Test
    void aWorldWithoutAnyRegionFileIsRefusedBeforeAnyChunkIsRead(Env env) throws IOException {
        Files.createDirectories(worlds.resolve("bare").resolve("region"));

        try (MapInstances instances = new MapInstances(env.process().instance(), worlds)) {
            assertThatThrownBy(() -> instances.forWorld("bare")).isInstanceOf(UnknownWorldException.class);
        }
    }
}
