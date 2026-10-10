package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.server.config.ConfigCheck;
import net.elytrarace.voyager.server.config.ServerSettings;

import net.minestom.server.MinecraftServer;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
        int exit = ConfigCheck.run(settings(data, worlds, "alpha_cup"), env.process().instance(), stream(captured));

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
        int exit = ConfigCheck.run(settings(data, worlds, "alpha_cup"), env.process().instance(), stream(captured));

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
        int exit = ConfigCheck.run(settings(data, worlds, "alpha_cup"), env.process().instance(), stream(captured));

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
        ServerSettings settings = settings(data, worlds, "alpha_cup");

        ConfigCheck.run(settings, env.process().instance(), stream(new ByteArrayOutputStream()));

        try (ServerSocket socket = new ServerSocket(settings.port())) {
            assertThat(socket.getLocalPort()).isEqualTo(settings.port());
        }
    }

    /**
     * The entry point the Gradle task runs. {@link VoyagerServer#configCheck} is the method {@code main}
     * delegates to, and its return value is the process's exit status, so these tests pin the two codes a
     * CI step reads without calling {@code System.exit}.
     */
    @Test
    void theEntryPointExitsZeroAndPrintsNoErrorForASoundConfiguration(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exit = VoyagerServer.configCheck(new String[0], properties(data, worlds),
                () -> env.process().instance(), stream(captured));

        assertThat(exit).isZero();
        assertThat(text(captured)).doesNotContain("ERROR").contains("config check passed");
    }

    @Test
    void theEntryPointExitsOneAndPrintsTheFileAndKeyOfAMalformedMapFile(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyCupsInto(data);
        Path broken = Files.createDirectories(data.resolve("maps")).resolve("a-broken.json");
        Files.writeString(broken, "");
        Files.createDirectories(worlds);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exit = VoyagerServer.configCheck(new String[0], properties(data, worlds),
                () -> env.process().instance(), stream(captured));

        assertThat(exit).isOne();
        assertThat(text(captured)).contains("ERROR " + broken.toAbsolutePath() + " file:");
        assertThat(text(captured)).contains("config check failed");
    }

    /**
     * A refused setting ends the run before Minestom is asked for anything: the instance supplier is the
     * only way the check reaches the game's registries, so a count of zero proves none was started.
     */
    @Test
    void theEntryPointExitsOneWithoutInitialisingMinestomWhenASettingIsRefused() throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        AtomicInteger suppliedInstances = new AtomicInteger();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exit = VoyagerServer.configCheck(new String[] {"127.0.0.1", "abc"}, properties(data, worlds),
                () -> {
                    suppliedInstances.incrementAndGet();
                    return null;
                }, stream(captured));

        assertThat(exit).isOne();
        assertThat(text(captured)).contains("ERROR command line port:");
        assertThat(suppliedInstances).hasValue(0);
    }

    /**
     * The check-only path never reaches the game server's start: the world check initialises the registries
     * and stops there, so the server reports itself as not started afterwards.
     */
    @Test
    void theEntryPointLeavesTheGameServerNotStarted(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);
        AtomicInteger suppliedInstances = new AtomicInteger();

        VoyagerServer.configCheck(new String[0], properties(data, worlds), () -> {
            suppliedInstances.incrementAndGet();
            return env.process().instance();
        }, stream(new ByteArrayOutputStream()));

        assertThat(suppliedInstances).hasValue(1);
        assertThat(MinecraftServer.isStarted()).isFalse();
    }

    /**
     * Boot refuses for the cup it plays and warns about the rest: a malformed cup file that is not played
     * does not appear among the refusals.
     */
    @Test
    void aBrokenUnplayedCupDoesNotRefuseBoot(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);
        Files.writeString(data.resolve("cups").resolve("zz-broken.json"), "");

        List<ConfigProblem> refusals = ConfigCheck.bootRefusals(settings(data, worlds, "alpha_cup"),
                env.process().instance());

        assertThat(refusals).isEmpty();
    }

    /**
     * A played cup that names a map nothing provides refuses boot, and only its own entry is listed: the
     * unplayed cup file that does not parse is left to the warning.
     */
    @Test
    void aBrokenPlayedCupRefusesBootWithItsOwnEntryAndNotAnUnplayedCupFile(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);
        Path played = data.resolve("cups").resolve("played.json");
        Files.writeString(played, """
                {"name": "played", "mode": "RACE", "mapNames": ["no-such-map"]}
                """);
        Files.writeString(data.resolve("cups").resolve("zz-broken.json"), "");

        List<ConfigProblem> refusals = ConfigCheck.bootRefusals(settings(data, worlds, "played"),
                env.process().instance());

        assertThat(refusals).extracting(ConfigProblem::source, ConfigProblem::key).containsExactly(
                tuple(played.toAbsolutePath().toString(), "no-such-map"));
    }

    /**
     * Nothing named and two cup files, one of them broken: no cup can be called the one played, so the
     * ambiguity refuses boot and the broken file is listed beside it.
     */
    @Test
    void anUnnamedSelectionWithABrokenSecondCupFileRefusesBoot(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);
        Path broken = Files.writeString(data.resolve("cups").resolve("zz-broken.json"), "");
        ServerSettings unnamed = new ServerSettings("127.0.0.1", 25572, data, worlds, Optional.empty(), false);

        List<ConfigProblem> refusals = ConfigCheck.bootRefusals(unnamed, env.process().instance());

        assertThat(refusals).extracting(ConfigProblem::key)
                .containsExactlyInAnyOrder("file", ServerSettings.CUP_PROPERTY);
        assertThat(refusals).extracting(ConfigProblem::source).contains(broken.toAbsolutePath().toString());
    }

    private static Map<String, String> properties(Path data, Path worlds) {
        return Map.of(ServerSettings.DATA_PATH_PROPERTY, data.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString(),
                ServerSettings.CUP_PROPERTY, "alpha_cup");
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

    /**
     * A minimum that is not a number refuses the validate-and-exit run with status one, before Minestom is
     * initialised: the instance supplier is never asked.
     */
    @Test
    void nonNumericMinimumExitsWithStatusOne() throws IOException {
        Path data = Files.createDirectories(root.resolve("data"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exit = VoyagerServer.configCheck(new String[0], Map.of(
                ServerSettings.DATA_PATH_PROPERTY, data.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString(),
                ServerSettings.MIN_RACERS_PROPERTY, "two"),
                () -> {
                    throw new AssertionError("the settings were refused, so Minestom must not be initialised");
                },
                stream(captured));

        assertThat(exit).isOne();
        assertThat(text(captured)).contains("VOYAGER_MIN_PLAYERS");
    }
}
