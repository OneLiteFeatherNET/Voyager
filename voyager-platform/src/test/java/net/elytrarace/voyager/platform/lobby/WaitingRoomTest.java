package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.cup.TickPipeline;
import net.elytrarace.voyager.platform.cup.TickStep;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate.Situation;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The waiting room on a live server, ticked explicitly: one {@code pipeline.run()} and one {@code room.tick()} per
 * step, as the server's guarded tick runs them. A lobby is ten seconds, which is two hundred ticks, and a race is
 * five seconds, so every scenario is a few hundred ticks and no wall-clock time passes.
 *
 * <p>Racers are connected to the first map's world and their online set is a list the test edits, so a leave is
 * the same change the server sees when a player disconnects.
 */
@EnvTest
class WaitingRoomTest {

    private static final String ARENA = "waiting_arena";
    private static final String OTHER = "waiting_other";
    private static final Vec3 ARENA_SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 OTHER_SPAWN = new Vec3(-37.5, 71.0, -88.5);
    private static final Duration STEP = Duration.ofMillis(50);
    private static final BoostConfig BOOST = new BoostConfig(4, 9);
    private static final int MINIMUM = 2;

    private static final MapDefinition ARENA_RUN = new MapDefinition("arena-run", ARENA, ARENA_SPAWN,
            List.of(new Ring(0, ARENA_SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), BOOST, new GuideLine(List.of(), 2, 1.0));

    private static final MapDefinition OTHER_RUN = new MapDefinition("other-run", OTHER, OTHER_SPAWN,
            List.of(new Ring(0, OTHER_SPAWN.plus(new Vec3(0, 0, 10)), new Vec3(0, 0, 1), 6.0, 13, RingType.STANDARD)),
            Duration.ofMillis(650), BOOST, new GuideLine(List.of(), 4, 1.5));

    private static final CupDefinition TOUR = new CupDefinition("waiting_tour", List.of("arena-run", "other-run"),
            GameMode.RACE);

    /** Lobby 10 s (200 ticks), race 5 s (100 ticks), results 100 ms. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofMillis(100), Duration.ofMillis(100));

    /** How many ticks a whole cup may take before the test gives up, so a hang fails instead of running on. */
    private static final int BUDGET = 2_000;

    @TempDir
    Path tempDir;

    /** Who is online now: the set the cup and the room read. */
    private final List<Player> online = new ArrayList<>();

    /** Everybody ever connected, so teardown removes a racer who left as well as one still online. */
    private final List<Player> everyRacer = new ArrayList<>();
    private MapInstances worlds;

    @AfterEach
    void releaseTheWorlds() {
        everyRacer.forEach(racer -> racer.remove(true));
        everyRacer.clear();
        online.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Starting
    // ------------------------------------------------------------------------------------------

    @Test
    void joiningAloneWithMinimumTwoStartsNoCup(Env env) throws IOException {
        Fixture fixture = fixture(env);

        fixture.join();
        fixture.tick();

        assertThat(fixture.session.running()).isFalse();
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.WAITING);
    }

    @Test
    void secondJoinStartsTheCountdown(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();

        fixture.tick();

        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COUNTDOWN);
    }

    @Test
    void launchComesAfterTheLobbyLength(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();

        fixture.ticks(199);
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COMMITTED);

        fixture.ticks(1);
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.RUNNING);
    }

    /** An operator's start with nobody online is not an abort: the cup waits until a racer has been online. */
    @Test
    void operatorStartWithNoRacersIsNotAbortedAtOnce(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.session.start(true);

        fixture.tick();

        assertThat(fixture.session.running()).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // The countdown
    // ------------------------------------------------------------------------------------------

    @Test
    void leavingBeforeTheCommitWindowCancelsAndTheRestartHasTheFullLobby(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        Player second = fixture.join();
        fixture.tick();
        fixture.ticks(100);

        fixture.leave(second);
        fixture.tick();
        assertThat(fixture.session.running()).isFalse();

        fixture.join(second);
        fixture.tick();
        fixture.ticks(139);
        assertThat(fixture.session.situation(Duration.ofSeconds(3)))
                .describedAs("a full lobby: 3.05 s are still ahead, so the start is not committed")
                .isEqualTo(Situation.COUNTDOWN);
    }

    @Test
    void leavingInTheLastThreeSecondsStillLaunches(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player first = fixture.join();
        Player second = fixture.join();
        fixture.tick();
        fixture.ticks(150);

        fixture.leave(second);
        fixture.tick();
        fixture.ticks(50);

        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.RUNNING);
        assertThat(fixture.runs.of(first.getUuid())).isPresent();
    }

    // ------------------------------------------------------------------------------------------
    // A running cup
    // ------------------------------------------------------------------------------------------

    @Test
    void lastRacerLeavingMidCupAborts(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player first = fixture.join();
        Player second = fixture.join();
        fixture.tick();
        fixture.ticks(200);

        fixture.leave(first);
        fixture.leave(second);
        fixture.tick();

        assertThat(fixture.session.running()).isFalse();
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.WAITING);
    }

    @Test
    void leavingBetweenMapsKeepsTheCupRunning(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        Player second = fixture.join();
        fixture.tick();
        fixture.ticks(200 + 100 + 1);

        fixture.leave(second);
        fixture.tick();

        assertThat(fixture.session.running()).isTrue();
    }

    @Test
    void joinDuringARunningCupIsToldJoinedMidCup(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();
        fixture.ticks(200);
        TestConnection late = env.createConnection();
        Collector<SystemChatPacket> chat = late.trackIncoming(SystemChatPacket.class);

        fixture.join(late);
        fixture.tick();

        assertThat(chat.collect().stream().map(SystemChatPacket::message).toList())
                .contains(Messages.joinedMidCup());
    }

    @Test
    void waitingRacerStandsOnTheFirstMapSpawnWithNoElytra(Env env) throws IOException {
        Fixture fixture = fixture(env);

        Player racer = fixture.join();

        assertThat(racer.getPosition().distance(new Pos(ARENA_SPAWN.x(), ARENA_SPAWN.y(), ARENA_SPAWN.z())))
                .isLessThan(1.0);
        assertThat(racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isNotEqualTo(Material.ELYTRA);
    }

    // ------------------------------------------------------------------------------------------
    // The end of a cup
    // ------------------------------------------------------------------------------------------

    @Test
    void finishedCupWithOneOnlineWaits(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        Player second = fixture.join();
        fixture.tick();
        fixture.ticks(200 + 100 + 1);
        fixture.leave(second);

        fixture.playUntilCupFinishes();
        fixture.tick();

        assertThat(fixture.session.running()).isFalse();
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.WAITING);
    }

    @Test
    void finishedCupWithTwoOnlineStartsTheNextCountdown(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();

        fixture.playUntilCupFinishes();

        assertThat(fixture.session.cupFinished()).isFalse();
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COUNTDOWN);
    }

    @Test
    void describeNamesTheOnlineRacersTheMinimumAndTheSituation(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();

        assertThat(fixture.room.describe()).isEqualTo("room: 1 of 2 racer(s) online, waiting");
    }

    // ------------------------------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------------------------------

    private Fixture fixture(Env env) throws IOException {
        writeWorld(env, ARENA, ARENA_SPAWN);
        writeWorld(env, OTHER, OTHER_SPAWN);
        MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        this.worlds = instances;
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);
        CatalogHolder holder = new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of("arena-run", ARENA_RUN, "other-run", OTHER_RUN), Map.of(TOUR.name(), TOUR)),
                TOUR, Instant.EPOCH));

        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(() -> online, boosts), new FlightTracker(), new MinestomCollisionSpace(blocks));
        CupSession session = new CupSession(holder, instances, transition, runs, flight, blocks, boosts, TIMINGS,
                STEP, () -> online);
        TickPipeline pipeline = TickPipeline.of(List.of(new TickStep("phase advance", session::advancePhase)));
        WaitingRoom room = new WaitingRoom(session, () -> online, MINIMUM,
                racer -> racer.teleport(new Pos(ARENA_SPAWN.x(), ARENA_SPAWN.y(), ARENA_SPAWN.z())).join());
        return new Fixture(session, pipeline, room, runs, instances, env);
    }

    /** Writes a world with one floor block under the spawn, through a Falco loader of its own. */
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

    private final class Fixture {

        private final CupSession session;
        private final TickPipeline pipeline;
        private final WaitingRoom room;
        private final RaceRuns runs;
        private final MapInstances instances;
        private final Env env;

        private Fixture(CupSession session, TickPipeline pipeline, WaitingRoom room, RaceRuns runs,
                MapInstances instances, Env env) {
            this.session = session;
            this.pipeline = pipeline;
            this.room = room;
            this.runs = runs;
            this.instances = instances;
            this.env = env;
        }

        /** Connects a racer to the first map's spawn, as the join handler does: prepared, then offered to the room. */
        Player join() {
            return join(env.createConnection());
        }

        Player join(TestConnection connection) {
            Instance instance = instances.forWorld(ARENA);
            instance.loadChunk(Math.floorDiv((int) Math.floor(ARENA_SPAWN.x()), 16), Math.floorDiv((int) Math.floor(ARENA_SPAWN.z()), 16)).join();
            Player racer = connection.connect(instance, new Pos(ARENA_SPAWN.x(), ARENA_SPAWN.y(), ARENA_SPAWN.z()));
            everyRacer.add(racer);
            online.add(racer);
            Racers.prepare(racer);
            room.joined(racer);
            return racer;
        }

        /** Rejoins a racer who has left, on their own connection. */
        void join(Player racer) {
            online.add(racer);
            room.joined(racer);
        }

        void leave(Player racer) {
            online.remove(racer);
        }

        /** One server tick: the cup's pipeline, then the waiting room, in the order the server runs them. */
        void tick() {
            pipeline.run();
            room.tick();
        }

        void ticks(int count) {
            for (int tick = 0; tick < count; tick++) {
                tick();
            }
        }

        /**
         * Ticks until the cup that is running has finished, then one more room tick, which is the tick that answers
         * for the finished cup. The budget is a guard against a hang, not a length anything is asserted on.
         */
        void playUntilCupFinishes() {
            for (int tick = 0; tick < BUDGET; tick++) {
                pipeline.run();
                boolean finished = session.cupFinished();
                room.tick();
                if (finished) {
                    return;
                }
            }
            throw new AssertionError("the cup did not finish within %s ticks".formatted(BUDGET));
        }
    }
}
