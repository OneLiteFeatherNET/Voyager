package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.lobby.LobbyTransition;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.exception.UnknownWorldException;
import net.elytrarace.voyager.server.config.ServerSettings;
import net.elytrarace.voyager.server.inject.JoinArrival;
import io.avaje.inject.BeanScope;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The lobby as the server wires it: where a joining racer spawns, that the lobby world is loaded when the graph is built,
 * and that it is never written. Each test writes its own worlds under its own temp directory and drives no ticks: the
 * wiring under test is synchronous.
 */
@EnvTest
class LobbyWiringTest {

    private static final String LOBBY = "wiring_lobby";
    private static final String FIRST = "wiring_first";
    private static final Vec3 LOBBY_SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 FIRST_SPAWN = new Vec3(-37.5, 71.0, 88.5);
    private static final Vec3 HOLOGRAM = new Vec3(0.5, 70.0, 0.5);

    @TempDir
    Path tempDir;

    private MapInstances worlds;

    @AfterEach
    void releaseTheWorlds() {
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    @Test
    void aJoiningRacerSpawnsInTheLobbyInstanceAtTheLobbySpawn(Env env) throws IOException {
        writeWorld(env, LOBBY, LOBBY_SPAWN);
        writeWorld(env, FIRST, FIRST_SPAWN);
        MapInstances instances = openWorlds(env);
        Optional<LobbyTransition> lobby = Optional.of(new LobbyTransition(instances,
                new LobbyDefinition(LOBBY, LOBBY_SPAWN)));

        JoinArrival.Arrival arrival = JoinArrival.of(lobby, firstMap(), instances);

        assertThat(arrival.instance()).isSameAs(lobby.orElseThrow().instance());
        assertThat(arrival.position()).isEqualTo(Vectors.toMinestom(LOBBY_SPAWN).asPos());
    }

    @Test
    void withoutALobbyAJoiningRacerSpawnsInTheFirstMap(Env env) throws IOException {
        writeWorld(env, FIRST, FIRST_SPAWN);
        MapInstances instances = openWorlds(env);

        JoinArrival.Arrival arrival = JoinArrival.of(Optional.empty(), firstMap(), instances);

        assertThat(arrival.instance()).isSameAs(instances.forWorld(FIRST));
        assertThat(arrival.position()).isEqualTo(Vectors.toMinestom(FIRST_SPAWN).asPos());
    }

    @Test
    void theLobbyWorldIsLoadedWhenTheGraphIsBuilt(Env env) throws IOException {
        writeWorld(env, LOBBY, LOBBY_SPAWN);
        MapInstances instances = openWorlds(env);

        LobbyTransition lobby = new LobbyTransition(instances, new LobbyDefinition(LOBBY, LOBBY_SPAWN));

        assertThat(instances.forWorld(LOBBY)).isSameAs(lobby.instance());
        assertThat(env.process().instance().getInstances()).contains(lobby.instance());
    }

    @Test
    void aLobbyFolderThatIsNotThereStopsTheBootNamingIt(Env env) throws IOException {
        MapInstances instances = openWorlds(env);

        assertThatThrownBy(() -> new LobbyTransition(instances, new LobbyDefinition("wiring_missing", LOBBY_SPAWN)))
                .isInstanceOf(UnknownWorldException.class)
                .hasMessageContaining("wiring_missing");
    }

    @Test
    void theLobbyWorldIsNotWrittenWhenTheServerStops(Env env) throws IOException {
        writeWorld(env, LOBBY, LOBBY_SPAWN);
        Path lobbyFolder = tempDir.resolve("worlds").resolve(LOBBY);
        MapInstances instances = openWorlds(env);
        new LobbyTransition(instances, new LobbyDefinition(LOBBY, LOBBY_SPAWN));
        Map<Path, String> beforeStop = contentsOf(lobbyFolder);

        instances.close();
        worlds = null;

        assertThat(contentsOf(lobbyFolder)).isEqualTo(beforeStop);
    }

    /** The hologram stands at its configured position in the lobby instance once the graph is built, that is at boot. */
    @Test
    void theHologramIsSpawnedInTheLobbyInstanceAtBoot(Env env) throws IOException {
        writeWorld(env, LOBBY, LOBBY_SPAWN);
        ServerSettings settings = bootSettings();

        try (BeanScope scope = VoyagerServer.openGraph(settings)) {
            Instance lobby = scope.get(MapInstances.class).forWorld(LOBBY);

            assertThat(lobby.getEntities())
                    .anySatisfy(entity -> {
                        assertThat(entity.getEntityType()).isEqualTo(EntityType.TEXT_DISPLAY);
                        assertThat(entity.getPosition()).isEqualTo(Vectors.toMinestom(HOLOGRAM).asPos());
                    });
        }
    }

    /** The hologram is an entity, not part of the lobby world: stopping the server writes the lobby exactly as it was. */
    @Test
    void theHologramIsNotSavedWhenTheServerStops(Env env) throws IOException {
        writeWorld(env, LOBBY, LOBBY_SPAWN);
        Path lobbyFolder = tempDir.resolve("worlds").resolve(LOBBY);
        BeanScope scope = VoyagerServer.openGraph(bootSettings());
        Map<Path, String> beforeStop = contentsOf(lobbyFolder);

        scope.get(MapInstances.class).close();
        scope.close();

        assertThat(contentsOf(lobbyFolder)).isEqualTo(beforeStop);
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    /** The settings a boot reads: the shipped catalogue, the lobby world, and the hologram at {@link #HOLOGRAM}. */
    private ServerSettings bootSettings() throws IOException {
        Path data = tempDir.resolve("data");
        Path worlds = Files.createDirectories(tempDir.resolve("worlds"));
        ShippedCatalogue.copyMapsInto(data);
        ShippedCatalogue.copyCupsInto(data);
        return new ServerSettings("127.0.0.1", 25571, data, worlds, Optional.of("alpha_cup"), false,
                ServerSettings.PRODUCTION_MINIMUM_RACERS, Optional.of(new LobbyDefinition(LOBBY, LOBBY_SPAWN)),
                ServerSettings.DEFAULT_MAXIMUM_RACERS, Optional.of(HOLOGRAM));
    }

    private MapInstances openWorlds(Env env) {
        worlds = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        return worlds;
    }

    private static MapDefinition firstMap() {
        return new MapDefinition("wiring-first", FIRST, FIRST_SPAWN,
                List.of(new Ring(0, FIRST_SPAWN.plus(new Vec3(0.0, 0.0, 30.0)), new Vec3(0.0, 0.0, 1.0), 6.0, 7,
                        RingType.STANDARD)),
                Duration.ofSeconds(4), new BoostConfig(12, 25), new GuideLine(List.of(), 4, 2.0));
    }

    /**
     * Every regular file under {@code folder}, as hex, so a write of any file shows as a difference. Hex rather than the
     * bytes themselves, because an array compares by identity and two equal files would never match.
     */
    private static Map<Path, String> contentsOf(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            Map<Path, String> contents = new HashMap<>();
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                contents.put(folder.relativize(file), HexFormat.of().formatHex(Files.readAllBytes(file)));
            }
            return contents;
        }
    }

    /** Writes one world with a block under {@code spawn}, through a Falco loader of its own, as the other world tests do. */
    private void writeWorld(Env env, String world, Vec3 spawn) throws IOException {
        InstanceManager manager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = manager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        int x = (int) Math.floor(spawn.x());
        int y = (int) Math.floor(spawn.y()) - 1;
        int z = (int) Math.floor(spawn.z());
        try {
            instance.loadChunk(x >> 4, z >> 4).join();
            instance.setBlock(x, y, z, Block.GOLD_BLOCK);
            instance.saveChunksToStorage().join();
        } finally {
            manager.unregisterInstance(instance);
            writer.close();
        }
    }
}
