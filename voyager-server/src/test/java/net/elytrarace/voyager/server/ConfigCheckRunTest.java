package net.elytrarace.voyager.server;

import net.elytrarace.voyager.server.config.ConfigCheck;
import net.elytrarace.voyager.server.config.ServerSettings;

import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The validate-and-exit run: exit 0 for a configuration with no error, exit 1 for one with any, the
 * report on the stream it is given, and no socket bound. Each test has its own directories under its
 * own {@code @TempDir}; the shipped catalogue is copied in, so a passing test passes against the data
 * the server really boots with.
 */
@EnvTest
class ConfigCheckRunTest {

    private static final String SHIPPED_WORLD = "ElytraraceBlueAndRed";

    @TempDir
    Path root;

    @Test
    void exitsZeroAndNamesNoErrorForAConfigurationThatIsSound(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int exit = ConfigCheck.run(settings(data, worlds, "test_cup"), env.process().instance(), stream(captured));

        assertThat(exit).isZero();
        assertThat(text(captured)).doesNotContain("ERROR");
    }

    @Test
    void exitsOneAndPrintsTheAbsolutePathAndTheKeyOfAMalformedMapFile(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyCupsInto(data);
        Files.createDirectories(data.resolve("maps"));
        Path broken = Files.writeString(data.resolve("maps/a-broken.json"), "");
        Files.createDirectories(worlds);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int exit = ConfigCheck.run(settings(data, worlds, "test_cup"), env.process().instance(), stream(captured));

        assertThat(exit).isOne();
        assertThat(text(captured)).contains("ERROR " + broken.toAbsolutePath() + " file:");
    }

    @Test
    void exitsOneAndNamesTheMapFileAndTheWorldFieldWhenTheWorldIsMissing(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        Files.createDirectories(worlds);
        Path mapFile = data.resolve("maps/elytraraceblueandred.json");

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int exit = ConfigCheck.run(settings(data, worlds, "test_cup"), env.process().instance(), stream(captured));

        assertThat(exit).isOne();
        assertThat(text(captured)).contains("ERROR " + mapFile.toAbsolutePath() + " world:");
    }

    /**
     * The check opens worlds and reads their chunks, and must not bind the game's port while doing so.
     * Binding it here after the run proves the run left it free.
     */
    @Test
    void leavesTheConfiguredPortUnbound(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);
        ServerSettings settings = settings(data, worlds, "test_cup");

        ConfigCheck.run(settings, env.process().instance(), stream(new ByteArrayOutputStream()));

        try (ServerSocket socket = new ServerSocket(settings.port())) {
            assertThat(socket.getLocalPort()).isEqualTo(settings.port());
        }
    }

    private static ServerSettings settings(Path data, Path worlds, String cup) {
        return new ServerSettings("127.0.0.1", 25572, data, worlds, Optional.of(cup), false);
    }

    /** Writes one block into one chunk through Falco, the same way the platform's world tests do. */
    private static void writeRacetrack(Env env, Path worlds, String world) throws IOException {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(worlds.resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        try {
            instance.loadChunk(0, 0).join();
            instance.setBlock(3, 64, 5, Block.GOLD_BLOCK);
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }

    private static PrintStream stream(ByteArrayOutputStream captured) {
        return new PrintStream(captured, true, StandardCharsets.UTF_8);
    }

    private static String text(ByteArrayOutputStream captured) {
        return captured.toString(StandardCharsets.UTF_8);
    }
}
