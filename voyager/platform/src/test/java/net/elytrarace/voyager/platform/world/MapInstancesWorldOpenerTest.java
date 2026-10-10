package net.elytrarace.voyager.platform.world;

import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceManager;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reload side of {@link MapInstances}: opening a world without a cup asking for it, discarding one a
 * rejected reload opened, and noticing region files that changed after a world was opened.
 *
 * <p>Each world is a single empty region file. Opening a world reads no chunk, so nothing here needs a
 * real map; the region file's presence is what {@code holdsRegionData} checks.
 */
@EnvTest
class MapInstancesWorldOpenerTest {

    private static final String TRACK = "goldrush";
    private static final String OTHER_TRACK = "bluecanyon";

    @TempDir
    Path tempDir;

    @Test
    void holdsRegionDataIsTrueForAWorldWithARegionFile(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);

        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());

        assertThat(instances.holdsRegionData(TRACK)).isTrue();
    }

    @Test
    void holdsRegionDataIsFalseForAWorldDirectoryWithNoRegionFile(Env env) throws IOException {
        Files.createDirectories(worldsRoot().resolve(TRACK));

        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());

        assertThat(instances.holdsRegionData(TRACK)).isFalse();
    }

    @Test
    void holdsRegionDataDoesNotOpenTheWorld(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());

        instances.holdsRegionData(TRACK);

        assertThat(instances.isOpen(TRACK)).isFalse();
    }

    @Test
    void openMakesTheWorldOpenAndRegistersItsInstance(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);
        InstanceManager manager = env.process().instance();
        MapInstances instances = new MapInstances(manager, worldsRoot());

        instances.open(TRACK);

        assertThat(instances.isOpen(TRACK)).isTrue();
        assertThat(manager.getInstances()).containsExactly(instances.forWorld(TRACK));
        instances.close();
    }

    @Test
    void openingAnAlreadyOpenWorldKeepsTheSameInstance(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());
        Instance first = instances.forWorld(TRACK);

        instances.open(TRACK);

        assertThat(instances.forWorld(TRACK)).isSameAs(first);
        instances.close();
    }

    @Test
    void openRefusesAWorldWithNoRegionDataAndOpensNothing(Env env) throws IOException {
        Files.createDirectories(worldsRoot().resolve(TRACK));
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());

        assertThatThrownBy(() -> instances.open(TRACK)).hasMessageContaining(TRACK);
        assertThat(instances.isOpen(TRACK)).isFalse();
    }

    @Test
    void discardUnregistersTheNamedWorldAndLeavesAnotherOpen(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);
        writeRegionFile(OTHER_TRACK, "r.0.0.mca", 0);
        InstanceManager manager = env.process().instance();
        MapInstances instances = new MapInstances(manager, worldsRoot());
        Instance discarded = instances.forWorld(TRACK);
        Instance kept = instances.forWorld(OTHER_TRACK);

        instances.discard(TRACK);

        assertThat(manager.getInstances()).doesNotContain(discarded).contains(kept);
        assertThat(instances.isOpen(TRACK)).isFalse();
        assertThat(instances.isOpen(OTHER_TRACK)).isTrue();
        instances.close();
    }

    @Test
    void discardClosesTheLoaderOfTheNamedWorld(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());
        Instance instance = instances.forWorld(TRACK);
        FalcoAnvilLoader loader = instances.loaderFor(TRACK);

        instances.discard(TRACK);

        assertThatThrownBy(() -> loader.loadChunk(instance, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is closed");
    }

    @Test
    void discardOfAWorldThatIsNotOpenDoesNothing(Env env) {
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());

        instances.discard(TRACK);

        assertThat(instances.isOpen(TRACK)).isFalse();
    }

    @Test
    void regionDataIsNotChangedWhenNothingOnDiskMovedSinceTheWorldWasOpened(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());
        instances.forWorld(TRACK);

        assertThat(instances.regionDataChanged(TRACK)).isFalse();
        instances.close();
    }

    @Test
    void regionDataIsChangedWhenARegionFileGrewAfterTheWorldWasOpened(Env env) throws IOException {
        Path region = writeRegionFile(TRACK, "r.0.0.mca", 0);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());
        instances.forWorld(TRACK);

        Files.write(region, new byte[4096]);

        assertThat(instances.regionDataChanged(TRACK)).isTrue();
        instances.close();
    }

    @Test
    void regionDataIsNotChangedForAWorldThatIsNotOpen(Env env) throws IOException {
        writeRegionFile(TRACK, "r.0.0.mca", 0);
        MapInstances instances = new MapInstances(env.process().instance(), worldsRoot());

        assertThat(instances.regionDataChanged(TRACK)).isFalse();
    }

    private Path worldsRoot() {
        return tempDir.resolve("worlds");
    }

    private Path writeRegionFile(String world, String fileName, int size) throws IOException {
        Path region = worldsRoot().resolve(world).resolve("region");
        Files.createDirectories(region);
        Path file = region.resolve(fileName);
        Files.write(file, new byte[size]);
        return file;
    }
}
