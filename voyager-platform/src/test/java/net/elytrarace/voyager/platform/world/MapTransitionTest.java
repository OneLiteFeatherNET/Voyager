package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.platform.world.exception.UnknownWorldException;
import net.elytrarace.voyager.race.run.RaceRun;
import net.minestom.server.coordinate.ChunkRange;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.entity.EntityTeleportEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Collector;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MapTransition} on a live server, because everything it does only exists on one: an instance
 * switch, a chunk wait, a confirmed teleport.
 *
 * <h2>What the fixture is built to tell apart</h2>
 *
 * <p>Three maps over two worlds. {@code RIDGE_START} and {@code RIDGE_FINALE} name the <em>same</em>
 * world, which is the case {@link MapInstances} makes real by caching one instance per world name —
 * and the case {@code Player#setInstance} refuses outright, so a transition that always switched
 * instances cannot pass {@link #twoMapsInOneWorldMoveTheRacerWithoutSwitchingInstances()}.
 *
 * <p>Every spawn sits in a different chunk from every other, and {@code DUNE}'s and
 * {@code RIDGE_FINALE}'s are negative on an axis: above zero a chunk index computed by a shift and
 * one computed by a truncating divide agree, and a region index is the value that differs. The
 * spawns are also far enough apart that no two of them share a view distance, so the chunks around
 * one arrival are never already loaded from the last.
 *
 * <p>Each world carries exactly one block, directly under its own spawn, and the three blocks are of
 * three different types. That is what "the ground is under them" is asserted against: a transition
 * that landed a racer at the right coordinates in the wrong world would put them over air, and a
 * cache handing out the wrong instance would still satisfy a coordinate assertion.
 */
@EnvTest
class MapTransitionTest {

    private static final String RIDGE = "ridge_arena";
    private static final String DUNE = "dune_arena";

    private static final Vec3 RIDGE_START_SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 DUNE_SPAWN = new Vec3(-37.5, 71.0, 88.5);
    private static final Vec3 RIDGE_FINALE_SPAWN = new Vec3(211.5, 69.0, -134.5);

    private static final MapDefinition RIDGE_START = map("ridge-start", RIDGE, RIDGE_START_SPAWN);
    private static final MapDefinition DUNE_RUN = map("dune-run", DUNE, DUNE_SPAWN);
    private static final MapDefinition RIDGE_FINALE = map("ridge-finale", RIDGE, RIDGE_FINALE_SPAWN);

    private static final PlacedBlock RIDGE_START_FLOOR = floorUnder(RIDGE_START_SPAWN, Block.GOLD_BLOCK);
    private static final PlacedBlock DUNE_FLOOR = floorUnder(DUNE_SPAWN, Block.DIAMOND_BLOCK);
    private static final PlacedBlock RIDGE_FINALE_FLOOR = floorUnder(RIDGE_FINALE_SPAWN, Block.EMERALD_BLOCK);

    private static final Duration TICK = Duration.ofMillis(50);

    @TempDir
    Path tempDir;

    private final List<Player> racers = new ArrayList<>();

    private MapInstances worlds;

    private Path worldsRoot() {
        return tempDir.resolve("worlds");
    }

    private MapInstances openWorlds(Env env) {
        worlds = new MapInstances(env.process().instance(), worldsRoot());
        return worlds;
    }

    /**
     * The racers go first. {@code InstanceManager} refuses to unregister an instance that still has
     * a player in it — the behaviour {@code MapInstancesTest} pins from the other side — so a test
     * that closed its worlds with somebody still standing in one would fail on the close rather than
     * on what it was asserting.
     */
    @AfterEach
    void releaseTheWorlds() {
        racers.forEach(racer -> racer.remove(true));
        racers.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Arriving
    // ------------------------------------------------------------------------------------------

    @Test
    void aRacerIsMovedIntoTheNextMapsWorldOntoItsSpawnWithGroundUnderThem(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        Player racer = connectTo(env, instances, RIDGE_START);

        transition.advanceTo(DUNE_RUN, List.of(racer));

        assertThat(racer.getInstance())
                .describedAs("the instance the dune world is cached under, not a second one")
                .isSameAs(instances.forWorld(DUNE));
        assertThat(racer.getPosition()).isEqualTo(spawnOf(DUNE_RUN));
        assertThat(blockUnder(racer))
                .describedAs("the ground the racer is standing on says which world they are in")
                .isEqualTo(DUNE_FLOOR.block());
        assertThat(runs.of(racer.getUuid())).contains(RaceRun.atStart());
    }

    /**
     * The hazard from Minestom issue #2017, pinned: a racer moved before the chunks are there has no
     * ground under the client and falls, which in a flight game reads as a physics bug. The whole
     * view distance is asserted and not only the spawn chunk — the client needs the ones around it
     * just as much, and the same-world branch loads exactly one of them on its own.
     */
    @Test
    void everyChunkTheClientNeedsAroundTheNewSpawnIsLoadedBeforeTheMoveReturns(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        MapTransition transition = new MapTransition(instances, new RaceRuns());
        Player racer = connectTo(env, instances, RIDGE_START);

        transition.advanceTo(DUNE_RUN, List.of(racer));

        assertChunksLoadedAround(instances.forWorld(DUNE), spawnOf(DUNE_RUN), racer.effectiveViewDistance());
    }

    /**
     * The other half of #2017's fix. Until the client echoes a teleport id back,
     * {@code PlayerPositionListener.processMovement} discards every position packet it sends — which
     * is what stops a position still describing the previous map from dragging the racer straight
     * back off the new spawn. A teleport sent unconfirmed carries id {@code -1}.
     */
    @Test
    void theArrivalTeleportIsConfirmedAndNamesTheNewSpawn(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        MapTransition transition = new MapTransition(instances, new RaceRuns());
        TestConnection connection = env.createConnection();
        Player racer = connect(env, connection, instances, RIDGE_START);
        Collector<PlayerPositionAndLookPacket> teleports =
                connection.trackIncoming(PlayerPositionAndLookPacket.class);

        transition.advanceTo(DUNE_RUN, List.of(racer));

        List<PlayerPositionAndLookPacket> sent = teleports.collect();
        assertThat(sent).isNotEmpty();
        PlayerPositionAndLookPacket arrival = sent.getLast();
        assertThat(arrival.position()).isEqualTo(spawnOf(DUNE_RUN));
        assertThat(arrival.teleportId())
                .describedAs("a confirmed teleport carries a real id; an unconfirmed one carries -1")
                .isPositive();
        assertThat(racer.getLastSentTeleportId()).isEqualTo(arrival.teleportId());
    }

    /**
     * One world backs more than one map, so {@link MapInstances} hands out the same instance for
     * both — and {@code Player#setInstance} rejects being given the instance the player is already
     * in. Advancing between two maps of one world is a legitimate case in which nothing has to be
     * switched at all.
     */
    @Test
    void twoMapsInOneWorldMoveTheRacerWithoutSwitchingInstances(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        Player racer = connectTo(env, instances, RIDGE_START);
        Instance ridge = instances.forWorld(RIDGE);

        transition.advanceTo(RIDGE_FINALE, List.of(racer));

        assertThat(racer.getInstance()).isSameAs(ridge);
        assertThat(racer.getPosition()).isEqualTo(spawnOf(RIDGE_FINALE));
        assertThat(blockUnder(racer)).isEqualTo(RIDGE_FINALE_FLOOR.block());
        assertChunksLoadedAround(ridge, spawnOf(RIDGE_FINALE), racer.effectiveViewDistance());
        assertThat(runs.of(racer.getUuid())).contains(RaceRun.atStart());
    }

    /**
     * The same-world move is confirmed too, and separately: the instance-switching branch gets its
     * confirmation from {@code Player#spawnPlayer}, and this branch has to send its own.
     */
    @Test
    void theSameWorldMoveIsConfirmedAsWell(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        MapTransition transition = new MapTransition(instances, new RaceRuns());
        TestConnection connection = env.createConnection();
        Player racer = connect(env, connection, instances, RIDGE_START);
        Collector<PlayerPositionAndLookPacket> teleports =
                connection.trackIncoming(PlayerPositionAndLookPacket.class);

        transition.advanceTo(RIDGE_FINALE, List.of(racer));

        PlayerPositionAndLookPacket arrival = teleports.collect().getLast();
        assertThat(arrival.position()).isEqualTo(spawnOf(RIDGE_FINALE));
        assertThat(arrival.teleportId()).isPositive();
    }

    /**
     * The same-world branch's chunk wait, observed at the only moment that can tell it apart from
     * Minestom's own. A teleport inside one instance loads the destination chunk and no more; the
     * surrounding ones are loaded by {@code Player#refreshCoordinate} <em>after</em> the position has
     * already changed, which is the racer standing over a hole while the world catches up. So the
     * question is not whether the chunks are ever there, it is whether they are there <em>first</em>
     * — and {@code EntityTeleportEvent} fires at the top of {@code Entity#teleport}, before the move.
     *
     * <p>Without the pre-load the whole suite stays green: in a test world every chunk is empty and
     * arrives fast enough that an assertion after the move cannot see the difference. On a real
     * racetrack read off disk it is the difference this class exists for.
     */
    @Test
    void theChunksAreThereBeforeTheRacerIsMovedWithinOneWorld(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        MapTransition transition = new MapTransition(instances, new RaceRuns());
        Player racer = connectTo(env, instances, RIDGE_START);
        Instance ridge = instances.forWorld(RIDGE);
        Pos finale = spawnOf(RIDGE_FINALE);
        AtomicReference<List<String>> missingWhenTheMoveBegan = new AtomicReference<>();
        env.process().eventHandler().addListener(EntityTeleportEvent.class, event -> {
            if (event.getEntity() == racer) {
                missingWhenTheMoveBegan.compareAndSet(null,
                        chunksMissingAround(ridge, finale, racer.effectiveViewDistance()));
            }
        });

        transition.advanceTo(RIDGE_FINALE, List.of(racer));

        assertThat(missingWhenTheMoveBegan.get())
                .describedAs("the teleport event fired, so the moment before the move was observed")
                .isNotNull();
        assertThat(missingWhenTheMoveBegan.get())
                .describedAs("chunks still missing at the instant the racer's position changed")
                .isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // The run the racer arrives with
    // ------------------------------------------------------------------------------------------

    @Test
    void theRunFromThePreviousMapDoesNotSurviveTheMove(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        Player racer = connectTo(env, instances, RIDGE_START);
        transition.advanceTo(RIDGE_START, List.of(racer));
        runs.advance(racer.getUuid(), RIDGE_START, RaceClock.startingAt(TICK).advanced(),
                RIDGE_START_SPAWN, true);
        assertThat(runs.of(racer.getUuid())).isNotEqualTo(Optional.of(RaceRun.atStart()));

        transition.advanceTo(DUNE_RUN, List.of(racer));

        assertThat(runs.of(racer.getUuid())).contains(RaceRun.atStart());
    }

    /**
     * The ordering inside {@link MapTransition#advanceTo}, made observable. Runs are dropped
     * <em>before</em> anybody moves, so there is no window in which a run over the previous map can
     * be advanced with a position on the new one. {@code PlayerSpawnEvent} fires from inside the
     * instance switch, which is exactly that window.
     */
    @Test
    void aRacerHoldsNoRunWhileTheyAreInTransit(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        Player racer = connectTo(env, instances, RIDGE_START);
        transition.advanceTo(RIDGE_START, List.of(racer));
        AtomicReference<Optional<RaceRun>> heldDuringTheMove = new AtomicReference<>();
        env.process().eventHandler().addListener(PlayerSpawnEvent.class,
                event -> heldDuringTheMove.compareAndSet(null, runs.of(event.getPlayer().getUuid())));

        transition.advanceTo(DUNE_RUN, List.of(racer));

        assertThat(heldDuringTheMove.get())
                .describedAs("the spawn event fired, so the window was actually observed")
                .isNotNull();
        assertThat(heldDuringTheMove.get()).isEmpty();
        assertThat(runs.of(racer.getUuid())).contains(RaceRun.atStart());
    }

    @Test
    void everyRacerIsMovedAndEveryRacerArrivesWithARun(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        Player leader = connectTo(env, instances, RIDGE_START);
        Player chaser = connectTo(env, instances, RIDGE_START);

        transition.advanceTo(DUNE_RUN, List.of(leader, chaser));

        assertThat(leader.getUuid()).isNotEqualTo(chaser.getUuid());
        for (Player racer : List.of(leader, chaser)) {
            assertThat(racer.getInstance()).isSameAs(instances.forWorld(DUNE));
            assertThat(racer.getPosition()).isEqualTo(spawnOf(DUNE_RUN));
            assertThat(runs.of(racer.getUuid())).contains(RaceRun.atStart());
        }
    }

    /**
     * The world is resolved before anything is dropped. A map naming a world that is not on disk is
     * the failure {@code UnknownWorldException} exists to make loud, and it has to leave the race it
     * interrupted exactly as it found it — not with every racer's run already gone and nobody moved.
     */
    @Test
    void aMapNamingAWorldThatIsNotThereFailsBeforeAnyRunIsDropped(Env env) throws IOException {
        writeWorlds(env);

        MapInstances instances = openWorlds(env);
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        Player racer = connectTo(env, instances, RIDGE_START);
        transition.advanceTo(RIDGE_START, List.of(racer));
        RaceRun inProgress = runs.advance(racer.getUuid(), RIDGE_START,
                RaceClock.startingAt(TICK).advanced(), RIDGE_START_SPAWN, true);
        MapDefinition missing = map("ghost-run", "no_such_world", DUNE_SPAWN);

        assertThatThrownBy(() -> transition.advanceTo(missing, List.of(racer)))
                .isInstanceOf(UnknownWorldException.class);

        assertThat(runs.of(racer.getUuid())).contains(inProgress);
        assertThat(racer.getInstance()).isSameAs(instances.forWorld(RIDGE));
        assertThat(racer.getPosition()).isEqualTo(spawnOf(RIDGE_START));
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    private static void assertChunksLoadedAround(Instance instance, Pos spawn, int viewDistance) {
        assertThat(chunksMissingAround(instance, spawn, viewDistance))
                .describedAs("chunks within %d of %s that the racer arrived without", viewDistance, spawn)
                .isEmpty();
    }

    private static List<String> chunksMissingAround(Instance instance, Pos spawn, int viewDistance) {
        List<String> missing = new ArrayList<>();
        ChunkRange.chunksInRange(spawn, viewDistance, (chunkX, chunkZ) -> {
            if (instance.getChunk(chunkX, chunkZ) == null) {
                missing.add("(%d, %d)".formatted(chunkX, chunkZ));
            }
        });
        return missing;
    }

    private static Block blockUnder(Player player) {
        Pos position = player.getPosition();
        return player.getInstance().getBlock(position.sub(0.0, 1.0, 0.0));
    }

    private static Pos spawnOf(MapDefinition map) {
        Vec3 spawn = map.spawn();
        return new Pos(spawn.x(), spawn.y(), spawn.z());
    }

    private Player connectTo(Env env, MapInstances instances, MapDefinition map) {
        return connect(env, env.createConnection(), instances, map);
    }

    private Player connect(Env env, TestConnection connection, MapInstances instances, MapDefinition map) {
        Instance instance = instances.forWorld(map.world());
        Pos spawn = spawnOf(map);
        instance.loadChunk(spawn.chunkX(), spawn.chunkZ()).join();
        Player racer = connection.connect(instance, spawn);
        racers.add(racer);
        return racer;
    }

    private void writeWorlds(Env env) throws IOException {
        writeWorld(env, RIDGE, List.of(RIDGE_START_FLOOR, RIDGE_FINALE_FLOOR));
        writeWorld(env, DUNE, List.of(DUNE_FLOOR));
    }

    /**
     * Writes a world through a Falco loader of its own, the same round trip {@code MapInstancesTest}
     * uses, so the fixture proves the on-disk format rather than a committed file that the code
     * reading it was written against.
     */
    private void writeWorld(Env env, String world, List<PlacedBlock> blocks) throws IOException {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(worldsRoot().resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);

        try {
            for (PlacedBlock placed : blocks) {
                instance.loadChunk(placed.chunkX(), placed.chunkZ()).join();
                instance.setBlock(placed.x(), placed.y(), placed.z(), placed.block());
            }
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }

    private static PlacedBlock floorUnder(Vec3 spawn, Block block) {
        return new PlacedBlock((int) Math.floor(spawn.x()), (int) Math.floor(spawn.y()) - 1,
                (int) Math.floor(spawn.z()), block);
    }

    private static MapDefinition map(String name, String world, Vec3 spawn) {
        return new MapDefinition(name, world, spawn,
                List.of(new Ring(0, new Vec3(spawn.x(), spawn.y(), spawn.z() + 30.0), new Vec3(0.0, 0.0, 1.0),
                        6.0, 7, RingType.STANDARD)),
                Duration.ofSeconds(4), new BoostConfig(12, 25));
    }

    private record PlacedBlock(int x, int y, int z, Block block) {

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }
}
