package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.LobbyDefinition;
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
import net.elytrarace.voyager.platform.handoff.HubHandoff;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.cup.MapTransition;
import net.elytrarace.voyager.platform.cup.RaceRuns;
import net.elytrarace.voyager.platform.stats.NoOpRaceRecorder;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate;
import net.elytrarace.voyager.race.flow.StartGate.Situation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private static final int MAXIMUM = 16;
    private static final String LOBBY = "waiting_lobby";
    private static final Vec3 LOBBY_SPAWN = new Vec3(20.5, 65.0, -12.5);

    private static final MapDefinition ARENA_RUN = new MapDefinition("arena-run", ARENA, ARENA_SPAWN,
            List.of(new Ring(0, ARENA_SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), BOOST, new GuideLine(List.of(), 2, 1.0));

    private static final MapDefinition OTHER_RUN = new MapDefinition("other-run", OTHER, OTHER_SPAWN,
            List.of(new Ring(0, OTHER_SPAWN.plus(new Vec3(0, 0, 10)), new Vec3(0, 0, 1), 6.0, 13, RingType.STANDARD)),
            Duration.ofMillis(650), BOOST, new GuideLine(List.of(), 4, 1.5));

    private static final CupDefinition TOUR = new CupDefinition("waiting_tour", List.of("arena-run", "other-run"),
            GameMode.RACE);

    /** The cup a reload offers: it plays from the next round, so the lobby keeps naming TOUR until a round promotes it. */
    private static final CupDefinition RELOADED_TOUR = new CupDefinition("waiting_tour_two",
            List.of("arena-run", "other-run"), GameMode.RACE);
    private static final LoadedCatalog RELOADED = new LoadedCatalog(new CatalogSnapshot(
            Map.of("arena-run", ARENA_RUN, "other-run", OTHER_RUN), Map.of(RELOADED_TOUR.name(), RELOADED_TOUR)),
            RELOADED_TOUR, Instant.EPOCH);

    /** Lobby 10 s (200 ticks), race 5 s (100 ticks), results 100 ms. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofMillis(100), Duration.ofMillis(100));

    /** A twenty second lobby, so a countdown can still have more than the ten seconds a full lobby leaves. */
    private static final RaceTimings LONG_LOBBY = new RaceTimings(
            Duration.ofSeconds(20), Duration.ofSeconds(5), Duration.ofMillis(100), Duration.ofMillis(100));

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

    /** The results stay up for the whole linger, and no racer is handed to the hub before its last tick. */
    @Test
    void nothingIsHandedOffWhileTheResultsLinger(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();
        fixture.playUntilCupFinishes();

        fixture.ticks(WaitingRoom.RESULTS_LINGER_TICKS - 1);

        assertThat(fixture.handedToHub).isEmpty();
    }

    /** The racers are still online during the results, and no countdown starts until they have been sent away. */
    @Test
    void noCountdownStartsWhileTheResultsLinger(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();
        fixture.playUntilCupFinishes();

        fixture.ticks(WaitingRoom.RESULTS_LINGER_TICKS - 1);

        assertThat(fixture.session.running()).isFalse();
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.WAITING);
    }

    /** When the linger ends, every racer online is handed to the hub, in the order they are online. */
    @Test
    void aFinishedCupHandsEveryRacerOnlineToTheHubWhenTheLingerEnds(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player first = fixture.join();
        Player second = fixture.join();
        fixture.tick();
        fixture.playUntilCupFinishes();

        fixture.ticks(WaitingRoom.RESULTS_LINGER_TICKS);

        assertThat(fixture.handedToHub).containsExactly(first.getUuid(), second.getUuid());
        assertThat(fixture.kicked).isEmpty();
    }

    /** A racer who joins while the results are up is online when the linger ends, so the hub gets them too. */
    @Test
    void aRacerWhoJoinsDuringTheLingerIsHandedOffWithTheRest(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player first = fixture.join();
        Player second = fixture.join();
        fixture.tick();
        fixture.playUntilCupFinishes();
        fixture.ticks(100);

        Player late = fixture.join();
        fixture.ticks(WaitingRoom.RESULTS_LINGER_TICKS - 100);

        assertThat(fixture.handedToHub).containsExactly(first.getUuid(), second.getUuid(), late.getUuid());
    }

    /** Racers who were handed away are still connected until the proxy moves them, so the room must not count them. */
    @Test
    void aRoomWhoseRacersWereHandedOffStartsNoCountdown(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();
        fixture.playUntilCupFinishes();
        fixture.ticks(WaitingRoom.RESULTS_LINGER_TICKS);
        assertThat(fixture.handedToHub).hasSize(2);

        fixture.tick();

        assertThat(fixture.session.situation(Duration.ofSeconds(3)))
                .describedAs("the two racers were sent away, so the room waits for new racers")
                .isEqualTo(Situation.WAITING);
    }

    /** After a handoff a new join starts the room as the minimum rule says, counting only the new racers. */
    @Test
    void aJoinAfterTheHandoffStartsTheRoomAsBefore(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();
        fixture.playUntilCupFinishes();
        fixture.ticks(WaitingRoom.RESULTS_LINGER_TICKS);

        fixture.join();
        fixture.join();
        fixture.tick();

        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COUNTDOWN);
    }

    /** A cup that is racing is not finished, so the linger does not start and nobody is handed off. */
    @Test
    void aRunningCupHandsNobodyOff(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();
        fixture.join();
        fixture.tick();
        fixture.ticks(200 + 100 + 1);

        assertThat(fixture.session.running()).isTrue();
        assertThat(fixture.handedToHub).isEmpty();
    }

    /** A cup that finishes with nobody online hands nobody off: the hub move is for racers only. */
    @Test
    void aFinishedCupWithNobodyOnlineHandsNobodyOff(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.session.start(true);

        fixture.playUntilCupFinishes();
        fixture.ticks(WaitingRoom.RESULTS_LINGER_TICKS);

        assertThat(fixture.handedToHub).isEmpty();
        assertThat(fixture.kicked).isEmpty();
    }

    @Test
    void describeNamesTheOnlineRacersTheMinimumAndTheSituation(Env env) throws IOException {
        Fixture fixture = fixture(env);
        fixture.join();

        assertThat(fixture.room.describe()).isEqualTo("room: 1 of 2 racer(s) online, waiting");
    }

    // ------------------------------------------------------------------------------------------
    // The lobby world, the join line and the full lobby
    // ------------------------------------------------------------------------------------------

    /**
     * A join is announced to every racer online, with the count after the join and the maximum. A join that follows
     * another inside the join window is held until the window closes, and is then told as the single join it was.
     */
    @Test
    void aJoinSendsTheJoinLineToEveryRacer(Env env) throws IOException {
        Fixture fixture = fixture(env);
        TestConnection first = env.createConnection();
        Collector<SystemChatPacket> chat = first.trackIncoming(SystemChatPacket.class);
        fixture.join(first);

        Player secondRacer = fixture.join();
        fixture.ticks(JoinBurst.WINDOW_TICKS + 1);

        assertThat(chat.collect().stream().map(SystemChatPacket::message).toList())
                .contains(Messages.playerJoined(secondRacer.getUsername(), 2, MAXIMUM));
    }

    /** A maximum is soft: a join past it is announced past it, and the racer is not refused. */
    @Test
    void aJoinBeyondTheMaximumIsNotRefused(Env env) throws IOException {
        Fixture fixture = fixture(env, TIMINGS, 2, false);
        TestConnection first = env.createConnection();
        Collector<SystemChatPacket> chat = first.trackIncoming(SystemChatPacket.class);
        fixture.join(first);
        fixture.join();

        Player third = fixture.join();
        fixture.ticks(JoinBurst.WINDOW_TICKS + 1);

        assertThat(online).contains(third);
        assertThat(chat.collect().stream().map(SystemChatPacket::message).toList())
                .as("the two joins after the first are one line, counted past the maximum")
                .contains(Messages.playersJoined(2, 3, 2));
    }

    /** A full lobby with fifteen seconds of its countdown left is shortened to ten seconds from that tick. */
    @Test
    void aFullLobbyShortensACountdownWithMoreThanTenSecondsLeft(Env env) throws IOException {
        Fixture fixture = fixture(env, LONG_LOBBY, 3, false);
        fixture.join();
        fixture.join();
        fixture.ticks(100);
        assertThat(fixture.session.lobbyRemaining()).isGreaterThan(Duration.ofSeconds(10));

        fixture.join();
        fixture.tick();

        assertThat(fixture.session.lobbyRemaining()).isEqualTo(Duration.ofSeconds(10));
    }

    /** A finished cup moves no racer to the lobby world: the racers go to the hub, and the lobby stays for joins. */
    @Test
    void aFinishedCupDoesNotMoveRacersToTheLobbyWorld(Env env) throws IOException {
        Fixture fixture = fixture(env, TIMINGS, MAXIMUM, true);
        Player racer = fixture.join();
        fixture.join();
        fixture.playUntilCupFinishes();

        assertThat(racer.getInstance()).isNotSameAs(fixture.instances.forWorld(LOBBY));
    }

    // ------------------------------------------------------------------------------------------
    // The fallback: no lobby world, so the racers wait in the first map and the bar is shown there
    // ------------------------------------------------------------------------------------------

    /** With no lobby configured, a waiting racer reads the count on the action bar, standing in the first map. */
    @Test
    void withoutALobbyTheWaitingCountIsOnTheActionBarInTheFirstMap(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player racer = fixture.join();

        fixture.tick();

        assertThat(keyOf(fixture.hud.actionBarOf(racer.getUuid())))
                .describedAs("the racer stands in the first map, which is where the fallback action bar is shown")
                .startsWith("voyager.lobby.actionbar.waiting");
    }

    /**
     * Without a lobby the countdown is on the action bar too, and it clears at the commit: from then on the race's own
     * displays are the only ones a racer sees, as they are when a lobby is configured and the racer has entered the map.
     */
    @Test
    void withoutALobbyTheCountdownShowsUntilTheStartIsCommitted(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player racer = fixture.join();
        fixture.join();
        fixture.tick();
        assertThat(keyOf(fixture.hud.actionBarOf(racer.getUuid()))).isEqualTo("voyager.lobby.actionbar.countdown");

        for (int tick = 0; tick < BUDGET && fixture.session.situation(StartGate.COMMIT_WINDOW) != Situation.COMMITTED;
                tick++) {
            fixture.tick();
        }

        assertThat(fixture.session.situation(StartGate.COMMIT_WINDOW)).isEqualTo(Situation.COMMITTED);
        assertThat(fixture.hud.actionBarOf(racer.getUuid())).isEmpty();
    }

    /** A reload waits for the next round: the lobby names the cup that is loaded until a round promotes the reload. */
    @Test
    void aPendingReloadDoesNotChangeTheCupTheLobbyNames(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player racer = fixture.join();
        fixture.tick();

        fixture.holder.offer(RELOADED);
        fixture.tick();

        assertThat(cupOnSidebar(fixture, racer)).isEqualTo(TOUR.name());
    }

    /** A countdown keeps the cup it began with, even when a reload is applied while it runs. */
    @Test
    void aCountdownKeepsItsCupWhenAReloadIsApplied(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player racer = fixture.join();
        fixture.join();
        fixture.tick();
        assertThat(fixture.session.situation(Duration.ofSeconds(3))).isEqualTo(Situation.COUNTDOWN);

        fixture.holder.offer(RELOADED);
        fixture.tick();

        assertThat(cupOnSidebar(fixture, racer)).isEqualTo(TOUR.name());
    }

    /** A waiting racer who leaves is handed to the hub, and the room keeps no record that the racer was handed off. */
    @Test
    void aWaitingRacerLeavesToTheHubWithoutARecordedHandoff(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player racer = fixture.join();
        fixture.tick();

        assertThat(fixture.room.leaveToHub(racer)).isTrue();

        assertThat(fixture.handedToHub).containsExactly(racer.getUuid());
        assertThat(fixture.room.describe())
                .as("the racer is still counted: the room records no hand-off for a leave")
                .startsWith("room: 1 of ");
    }

    /** A racer who is racing cannot leave to the hub: the room refuses, and the racer is not handed away. */
    @Test
    void aRacerWhoIsRacingCannotLeaveToTheHub(Env env) throws IOException {
        Fixture fixture = fixture(env);
        Player racer = fixture.join();
        fixture.join();
        for (int tick = 0; tick < BUDGET && fixture.session.situation(StartGate.COMMIT_WINDOW) != Situation.RUNNING;
                tick++) {
            fixture.tick();
        }

        assertThat(fixture.room.leaveToHub(racer)).isFalse();
        assertThat(fixture.handedToHub).isEmpty();
    }

    /** The cup name the sidebar carries as its title. */
    private static String cupOnSidebar(Fixture fixture, Player racer) {
        return PlainTextComponentSerializer.plainText()
                .serialize(fixture.sidebar.sidebarOf(racer.getUuid()).orElseThrow().getTitle());
    }

    /** The translation key of an action bar line that is shown. */
    private static String keyOf(Optional<Component> line) {
        return ((TranslatableComponent) line.orElseThrow()).key();
    }

    // ------------------------------------------------------------------------------------------
    // The fixture
    // ------------------------------------------------------------------------------------------

    private Fixture fixture(Env env) throws IOException {
        return fixture(env, TIMINGS, MAXIMUM, false);
    }

    /**
     * A room on the first map's world, with the given lobby length and maximum. With {@code withLobby} the room returns
     * finished racers to a lobby world of its own; without it they go back to the first map's spawn, as before.
     */
    private Fixture fixture(Env env, RaceTimings timings, int maximum, boolean withLobby) throws IOException {
        writeWorld(env, ARENA, ARENA_SPAWN);
        writeWorld(env, OTHER, OTHER_SPAWN);
        writeWorld(env, LOBBY, LOBBY_SPAWN);
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
        CupSession session = new CupSession(holder, instances, transition, runs, flight, blocks, boosts, timings,
                STEP, () -> online, new NoOpRaceRecorder(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        TickPipeline pipeline = TickPipeline.of(List.of(new TickStep("phase advance", session::advancePhase)));
        LobbyTransition lobby = withLobby ? new LobbyTransition(instances, new LobbyDefinition(LOBBY, LOBBY_SPAWN)) : null;
        LobbyHud hud = new LobbyHud();
        List<UUID> handedToHub = new ArrayList<>();
        List<Player> kicked = new ArrayList<>();
        // The hub is installed: every racer the room sends is recorded as handed off. A warning is not expected here.
        HubHandoff hub = new HubHandoff(() -> Optional.of(handedToHub::add),
                (racer, reason) -> kicked.add(racer),
                message -> {
                    throw new AssertionError("no refused handoff was expected, but got: " + message);
                });
        LobbySidebar sidebar = new LobbySidebar();
        WaitingRoom room = new WaitingRoom(session, holder, () -> online, MINIMUM, maximum, lobby, hub, hud,
                sidebar, null, () -> instances.forWorld(ARENA));
        return new Fixture(session, holder, pipeline, room, hud, sidebar, runs, instances, env, handedToHub, kicked);
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
        private final CatalogHolder holder;
        private final TickPipeline pipeline;
        private final WaitingRoom room;
        private final LobbyHud hud;
        private final LobbySidebar sidebar;
        private final RaceRuns runs;
        private final MapInstances instances;
        private final Env env;
        /** The racers the room handed to the hub, in order. */
        private final List<UUID> handedToHub;
        /** The racers the room disconnected because no handoff ran or it refused. */
        private final List<Player> kicked;

        private Fixture(CupSession session, CatalogHolder holder, TickPipeline pipeline, WaitingRoom room, LobbyHud hud,
                LobbySidebar sidebar, RaceRuns runs,
                MapInstances instances, Env env, List<UUID> handedToHub, List<Player> kicked) {
            this.session = session;
            this.holder = holder;
            this.pipeline = pipeline;
            this.room = room;
            this.hud = hud;
            this.sidebar = sidebar;
            this.runs = runs;
            this.instances = instances;
            this.env = env;
            this.handedToHub = handedToHub;
            this.kicked = kicked;
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
