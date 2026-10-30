package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.platform.rotation.FileCupRotationStore;
import net.elytrarace.voyager.race.rotation.CupRotationStore;
import net.elytrarace.voyager.server.config.ConfigCheck;
import net.elytrarace.voyager.server.config.ConfigCheck.RotationBoot;
import net.elytrarace.voyager.server.config.CupRotationSettings;
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
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boot decision for a rotation, against the shipped catalogue and real worlds written into this test's own
 * directory. A world is present when its racetrack is on disk and missing when its folder is not. The state file, when a
 * test writes one, lives in the same temporary directory.
 */
@EnvTest
class RotationBootCheckTest {

    private static final String ALPHA = "elytrarace:alpha_cup";
    private static final String LEGACY = "elytrarace:legacy_cup";
    private static final String ALPHA_WORLD = "ElytraraceBlueAndRed";

    @TempDir
    Path root;

    @Test
    void aRotationWithEveryWorldPresentBootsAtItsFirstCupWithNoRefusal(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD, "PalmBeach", "DragonLobby", "Memoria");

        RotationBoot boot = fixture.boot(List.of(ALPHA, LEGACY), Optional.empty());

        assertThat(boot.plan()).isPresent();
        assertThat(boot.plan().orElseThrow().boot()).contains(ALPHA);
        assertThat(boot.plan().orElseThrow().skipped()).isEmpty();
        assertThat(errors(boot)).isEmpty();
    }

    @Test
    void aCupWithAMissingWorldIsSkippedWithOneWarningAndTheOtherCupPlays(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD);

        RotationBoot boot = fixture.boot(List.of(ALPHA, LEGACY), Optional.empty());

        assertThat(boot.plan()).isPresent();
        assertThat(boot.plan().orElseThrow().boot()).contains(ALPHA);
        assertThat(boot.plan().orElseThrow().skipped()).containsExactly(LEGACY);
        assertThat(worldWarnings(boot)).singleElement().asString().contains(LEGACY).contains("PalmBeach");
    }

    @Test
    void bootIsRefusedWhenNoCupOfTheRotationIsPlayable(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD);

        RotationBoot boot = fixture.boot(List.of(LEGACY), Optional.empty());

        assertThat(boot.plan()).isEmpty();
        assertThat(errors(boot)).singleElement().asString().contains(LEGACY).contains("PalmBeach");
    }

    @Test
    void aStateFileNamingTheArmedCupStartsTheRotationThere(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD, "PalmBeach", "DragonLobby", "Memoria");
        Path state = fixture.stateFile("elytrarace:legacy_cup");

        RotationBoot boot = fixture.boot(List.of(ALPHA, LEGACY), Optional.of(state));

        assertThat(boot.plan().orElseThrow().boot()).contains(LEGACY);
    }

    @Test
    void aStateFileNamingASkippedCupStartsAtTheFirstPlayableCupAndSaysSo(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD);
        Path state = fixture.stateFile("elytrarace:legacy_cup");

        RotationBoot boot = fixture.boot(List.of(ALPHA, LEGACY), Optional.of(state));

        assertThat(boot.plan().orElseThrow().boot()).contains(ALPHA);
        assertThat(boot.plan().orElseThrow().warnings()).singleElement().asString().contains(LEGACY);
    }

    @Test
    void aStateFileWrittenWithTheFlatNameBeforeTheKeysStillResumesThatCup(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD, "PalmBeach", "DragonLobby", "Memoria");
        Path state = fixture.stateFile("legacy_cup");

        RotationBoot boot = fixture.boot(List.of(ALPHA, LEGACY), Optional.of(state));

        assertThat(boot.plan().orElseThrow().boot()).contains(LEGACY);
    }

    @Test
    void checkModeReportsASkippedCupAsAWarningAndExitsZeroWhileAnotherCupPlays(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exit = fixture.check(List.of(ALPHA, LEGACY), new PrintStream(captured, true, StandardCharsets.UTF_8));

        assertThat(exit).as("a skipped cup is a warning, so the check exits zero").isZero();
        assertThat(captured.toString(StandardCharsets.UTF_8))
                .contains("WARNING")
                .contains(LEGACY)
                .contains("config check passed");
    }

    @Test
    void checkModeExitsOneWhenNoCupOfTheRotationIsPlayable(Env env) throws IOException {
        Fixture fixture = new Fixture(env, ALPHA_WORLD);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        int exit = fixture.check(List.of(LEGACY), new PrintStream(captured, true, StandardCharsets.UTF_8));

        assertThat(exit).isEqualTo(1);
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains("config check failed");
    }

    private static List<ConfigProblem> errors(RotationBoot boot) {
        return boot.problems().stream().filter(problem -> problem.severity() == Severity.ERROR).toList();
    }

    private static List<String> worldWarnings(RotationBoot boot) {
        return boot.problems().stream()
                .filter(problem -> problem.severity() == Severity.WARNING && problem.key().equals("world"))
                .map(ConfigProblem::message)
                .toList();
    }

    /** One test's directories: the shipped maps and cups, the worlds the test writes, and its state file. */
    private final class Fixture {

        private final Env env;
        private final Path data;
        private final Path worlds;

        Fixture(Env env, String... worldNames) throws IOException {
            this.env = env;
            this.data = Files.createDirectories(root.resolve("data"));
            this.worlds = Files.createDirectories(root.resolve("worlds"));
            ShippedCatalogue.copyMapsInto(data);
            ShippedCatalogue.copyCupsInto(data);
            // The legacy cup and its three maps, which the shipped alpha copy above does not include.
            for (String map : List.of("palm_beach.json", "dragon_lobby.json", "memoria.json")) {
                copyResource("/maps/" + map, data.resolve("maps").resolve(map));
            }
            copyResource("/cups/legacy_cup.json", data.resolve("cups").resolve("legacy_cup.json"));
            for (String world : worldNames) {
                writeRacetrack(world);
            }
        }

        private void copyResource(String resource, Path target) throws IOException {
            Files.createDirectories(target.getParent());
            try (InputStream in = RotationBootCheckTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("classpath resource %s", resource).isNotNull();
                Files.copy(in, target);
            }
        }

        Path stateFile(String cup) throws IOException {
            Path state = Files.createDirectories(root.resolve("state")).resolve("rotation");
            Files.writeString(state, cup + "\n");
            return state;
        }

        RotationBoot boot(List<String> cups, Optional<Path> stateFile) {
            CupRotationSettings rotation = new CupRotationSettings(cups, stateFile);
            CupRotationStore store = stateFile.<CupRotationStore>map(FileCupRotationStore::new)
                    .orElse(CupRotationStore.NONE);
            return ConfigCheck.rotationBoot(settings(), rotation, store, instances(), ConfigCheck.PROPERTY_SOURCE);
        }

        int check(List<String> cups, PrintStream out) {
            return ConfigCheck.runRotation(settings(), new CupRotationSettings(cups, Optional.empty()), instances(), out,
                    ConfigCheck.PROPERTY_SOURCE, List.of());
        }

        private ServerSettings settings() {
            return new ServerSettings("127.0.0.1", 25572, data, worlds, Optional.empty(), false);
        }

        private InstanceManager instances() {
            return env.process().instance();
        }

        private void writeRacetrack(String world) throws IOException {
            InstanceManager instanceManager = instances();
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
    }
}
