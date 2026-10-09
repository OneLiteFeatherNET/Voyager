package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.world.exception.WorldAlreadyExistsException;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoidWorldTemplateTest {

    @TempDir
    Path worlds;

    @Test
    void copyCreatesTheWorldFolderWithOneRegionFile() throws IOException {
        Path world = worlds.resolve("skyfortress");

        VoidWorldTemplate.copyTo(world);

        Path region = world.resolve("region").resolve("r.0.0.mca");
        assertThat(region).exists();
        assertThat(Files.size(region)).isEqualTo(8192);
        assertThat(WorldFolders.holdsRegionData(world.resolve("region"))).isTrue();
    }

    @Test
    void copyRefusesAWorldFolderThatAlreadyExists() throws IOException {
        Path world = Files.createDirectories(worlds.resolve("skyfortress"));

        assertThatThrownBy(() -> VoidWorldTemplate.copyTo(world)).isInstanceOf(WorldAlreadyExistsException.class);
    }

    @EnvTest
    void theCopiedWorldIsAcceptedByMapInstances(Env env) {
        Path world = worlds.resolve("skyfortress");
        VoidWorldTemplate.copyTo(world);

        try (MapInstances instances = new MapInstances(env.process().instance(), worlds)) {
            assertThat(instances.forWorld("skyfortress")).isNotNull();
        }
    }
}
