package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.cup.MapTransition;
import net.elytrarace.voyager.platform.cup.RaceRuns;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.race.run.RaceRun;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lobby transfers on a live instance manager, with two small worlds written under a {@link TempDir} as the map
 * transition's tests write theirs. Each test builds its own worlds and connections.
 */
@EnvTest
class LobbyTransitionTest {

    private static final String LOBBY = "lobby_hall";
    private static final String FIRST = "lobby_ridge";
    private static final Vec3 LOBBY_SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 FIRST_SPAWN = new Vec3(-37.5, 71.0, 88.5);
    private static final Vec3 ELSEWHERE_IN_LOBBY = new Vec3(10.5, 65.0, 9.5);

    @TempDir
    Path tempDir;

    private final List<Player> racers = new ArrayList<>();
    private MapInstances worlds;

    @AfterEach
    void releaseTheWorlds() {
        racers.forEach(racer -> racer.remove(true));
        racers.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    @Test
    void aRacerMovesFromTheLobbyToTheFirstMapWithAFreshRun(Env env) throws IOException {
        writeWorlds(env);
        MapInstances instances = openWorlds(env);
        LobbyTransition lobby = new LobbyTransition(instances, new LobbyDefinition(LOBBY, LOBBY_SPAWN));
        Player racer = connectTo(env, lobby.instance(), Vectors.toMinestom(LOBBY_SPAWN).asPos());
        RaceRuns runs = new RaceRuns();

        new MapTransition(instances, runs).advanceTo(firstMap(), List.of(racer));

        assertThat(racer.getInstance()).isSameAs(instances.forWorld(FIRST));
        assertThat(runs.of(racer.getUuid())).contains(RaceRun.atStart());
    }

    @Test
    void aFinishedCupReturnsTheRacerToTheLobbySpawn(Env env) throws IOException {
        writeWorlds(env);
        MapInstances instances = openWorlds(env);
        LobbyTransition lobby = new LobbyTransition(instances, new LobbyDefinition(LOBBY, LOBBY_SPAWN));
        Player racer = connectTo(env, instances.forWorld(FIRST), Vectors.toMinestom(FIRST_SPAWN).asPos());

        lobby.returnTo(List.of(racer));

        assertThat(racer.getInstance()).isSameAs(lobby.instance());
        assertThat(racer.getPosition()).isEqualTo(Vectors.toMinestom(LOBBY_SPAWN).asPos());
    }

    @Test
    void aRacerAlreadyInTheLobbyIsRepositionedOnTheSpawn(Env env) throws IOException {
        writeWorlds(env);
        MapInstances instances = openWorlds(env);
        LobbyTransition lobby = new LobbyTransition(instances, new LobbyDefinition(LOBBY, LOBBY_SPAWN));
        Player racer = connectTo(env, lobby.instance(), Vectors.toMinestom(ELSEWHERE_IN_LOBBY).asPos());

        lobby.returnTo(List.of(racer));

        assertThat(racer.getInstance()).isSameAs(lobby.instance());
        assertThat(racer.getPosition()).isEqualTo(Vectors.toMinestom(LOBBY_SPAWN).asPos());
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    private MapInstances openWorlds(Env env) {
        worlds = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        return worlds;
    }

    private Player connectTo(Env env, Instance instance, Pos spawn) {
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(instance, spawn);
        racers.add(racer);
        return racer;
    }

    private static MapDefinition firstMap() {
        return new MapDefinition("ridge", FIRST, FIRST_SPAWN,
                List.of(new Ring(0, FIRST_SPAWN.plus(new Vec3(0.0, 0.0, 30.0)), new Vec3(0.0, 0.0, 1.0), 6.0, 7,
                        RingType.STANDARD)),
                Duration.ofSeconds(4), new BoostConfig(12, 25), new GuideLine(List.of(), 4, 2.0));
    }

    private void writeWorlds(Env env) throws IOException {
        writeWorld(env, LOBBY, LOBBY_SPAWN);
        writeWorld(env, FIRST, FIRST_SPAWN);
    }

    /** Writes one world with a block under {@code spawn}, through a Falco loader of its own, as the map tests do. */
    private void writeWorld(Env env, String world, Vec3 spawn) throws IOException {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        int x = (int) Math.floor(spawn.x());
        int y = (int) Math.floor(spawn.y()) - 1;
        int z = (int) Math.floor(spawn.z());
        try {
            instance.loadChunk(x >> 4, z >> 4).join();
            instance.setBlock(x, y, z, Block.STONE);
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }
}
