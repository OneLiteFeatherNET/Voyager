package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby world in the configuration check: no lobby is a warning, a lobby is checked like a map's world, and the
 * lobby may not be a map's world. Each test has its own directories under its own temp root, with the shipped catalogue
 * copied in, so the cup and the maps are the ones the server really boots with.
 */
@EnvTest
class ConfigCheckLobbyTest {

    private static final String SHIPPED_WORLD = "ElytraraceBlueAndRed";
    private static final String LOBBY_WORLD = "lobby_hall";
    private static final Vec3 LOBBY_SPAWN = new Vec3(3.5, 64.0, 5.5);

    @TempDir
    Path root;

    @Test
    void noLobbyIsAWarningThatNamesBothSettings(Env env) throws IOException {
        Fixture fixture = fixture(env);

        List<ConfigProblem> problems = ConfigCheck.catalogueProblems(fixture.settings(Optional.empty()),
                env.process().instance());

        assertThat(problems).noneMatch(problem -> problem.severity() == Severity.ERROR);
        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.WARNING);
            assertThat(problem.key()).isEqualTo(ServerSettings.LOBBY_WORLD_PROPERTY);
            assertThat(problem.format()).contains(ServerSettings.LOBBY_WORLD_PROPERTY)
                    .contains(ServerSettings.LOBBY_SPAWN_PROPERTY);
        });
    }

    @Test
    void aLobbyThatNamesAMapsWorldIsAnErrorNamingTheMap(Env env) throws IOException {
        Fixture fixture = fixture(env);
        MapDefinition map = CatalogLoader.read(fixture.data()).snapshot().maps().values().iterator().next();

        List<ConfigProblem> problems = ConfigCheck.catalogueProblems(
                fixture.settings(Optional.of(new LobbyDefinition(map.world(), LOBBY_SPAWN))),
                env.process().instance());

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(ServerSettings.LOBBY_WORLD_PROPERTY);
            assertThat(problem.format()).contains(ServerSettings.LOBBY_WORLD_PROPERTY).contains(map.name());
        });
    }

    @Test
    void aLobbyWorldWithoutRegionDataIsAnErrorNamingTheFolder(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Path emptyLobby = Files.createDirectories(fixture.worlds().resolve(LOBBY_WORLD));

        List<ConfigProblem> problems = ConfigCheck.catalogueProblems(
                fixture.settings(Optional.of(new LobbyDefinition(LOBBY_WORLD, LOBBY_SPAWN))),
                env.process().instance());

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.key()).isEqualTo(ServerSettings.LOBBY_WORLD_PROPERTY);
            assertThat(problem.format()).contains(emptyLobby.toAbsolutePath().toString());
        });
    }

    @Test
    void aLobbyFolderThatIsNotThereIsAnError(Env env) throws IOException {
        Fixture fixture = fixture(env);

        List<ConfigProblem> problems = ConfigCheck.catalogueProblems(
                fixture.settings(Optional.of(new LobbyDefinition("lobby_missing", LOBBY_SPAWN))),
                env.process().instance());

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
            assertThat(problem.format()).contains("lobby_missing");
        });
    }

    /**
     * A sound lobby and a sound map world are both checked, one after the other, and both are closed again: the
     * instance manager holds exactly the instances it held before the check.
     */
    @Test
    void theLobbyAndTheMapWorldAreCheckedOneAfterTheOther(Env env) throws IOException {
        Fixture fixture = fixture(env);
        writeRacetrack(env, fixture.worlds(), LOBBY_WORLD);
        int before = env.process().instance().getInstances().size();

        List<ConfigProblem> problems = ConfigCheck.catalogueProblems(
                fixture.settings(Optional.of(new LobbyDefinition(LOBBY_WORLD, LOBBY_SPAWN))),
                env.process().instance());

        assertThat(problems).noneMatch(problem -> problem.severity() == Severity.ERROR);
        assertThat(env.process().instance().getInstances()).hasSize(before);
    }

    @Test
    void aSpawnThatCannotBeReadIsKeyedBySetting() {
        List<ConfigProblem> problems = ConfigCheck.settingsProblems(new String[0], Map.of(
                ServerSettings.DATA_PATH_PROPERTY, root.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, root.toString(),
                ServerSettings.LOBBY_WORLD_PROPERTY, LOBBY_WORLD,
                ServerSettings.LOBBY_SPAWN_PROPERTY, "0,64"));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.key()).isEqualTo(ServerSettings.LOBBY_SPAWN_PROPERTY);
            assertThat(problem.format()).contains("0,64");
        });
    }

    @Test
    void aMaximumBelowTheMinimumIsKeyedByTheMaximum() {
        List<ConfigProblem> problems = ConfigCheck.settingsProblems(new String[0], Map.of(
                ServerSettings.DATA_PATH_PROPERTY, root.toString(),
                ServerSettings.WORLDS_PATH_PROPERTY, root.toString(),
                ServerSettings.MIN_RACERS_PROPERTY, "3",
                ServerSettings.MAX_PLAYERS_PROPERTY, "2"));

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.key()).isEqualTo(ServerSettings.MAX_PLAYERS_PROPERTY);
            assertThat(problem.format()).contains(ServerSettings.MIN_RACERS_PROPERTY);
        });
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    private Fixture fixture(Env env) throws IOException {
        Path data = root.resolve("data");
        Path worlds = root.resolve("worlds");
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        writeRacetrack(env, worlds, SHIPPED_WORLD);
        return new Fixture(data, worlds);
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

    private record Fixture(Path data, Path worlds) {

        ServerSettings settings(Optional<LobbyDefinition> lobby) {
            return new ServerSettings("127.0.0.1", 25573, data, worlds, Optional.of("alpha_cup"), false, 2, lobby, 16);
        }
    }
}
