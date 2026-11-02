package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.run.RaceRun;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.BossBarPacket;
import net.minestom.testing.Env;
import net.minestom.testing.Collector;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GO waits for racers who are still loading their map. Played through the cup on a live test server, ticked by hand, with
 * the chunk loads held by {@link GatedChunkLoader} so each test decides when a racer's placement completes.
 *
 * <p>Every tick count is the number of ticks the cup has been advanced since it was opened, so the lobby's last tick is
 * {@link #LOBBY_TICKS} and a held GO fires on {@code LOBBY_TICKS + HOLD_TICKS}.
 */
@EnvTest
class GoHoldTest {

    /** Lobby of two seconds, which is {@link #LOBBY_TICKS} ticks, a race of ten seconds, and a short end between maps. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofMillis(200), Duration.ofMillis(200));

    /** The lobby's length in ticks, which is when GO fires if every racer is placed in time. */
    private static final int LOBBY_TICKS = 40;

    /** A lobby of no length, which is an operator's skip: its GO fires on the first tick. */
    private static final RaceTimings NO_LOBBY = new RaceTimings(
            Duration.ZERO, Duration.ofSeconds(10), Duration.ofMillis(200), Duration.ofMillis(200));

    /** The longest GO is held, in ticks: {@link RaceTimings#GO_HOLD_LIMIT} at 50 ms a tick. */
    private static final int HOLD_TICKS = 100;

    /** Ticks a placement may take on a live server before the test calls it a failure. */
    private static final int PLACEMENT_BUDGET = 200;

    /** Ticks GO may take to fire before the test calls it missing: comfortably past the hold. */
    private static final int GO_BUDGET = 1_000;

    @TempDir
    Path tempDir;

    /**
     * While a racer is still loading when the lobby would end, GO does not fire, the racer already placed stays on the
     * spawn, and the boss bar tells the loading racer what GO is waiting for.
     */
    @Test
    void goIsHeldWhileARacerIsStillLoadingAndThePlacedRacerWaitsAtTheSpawn(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, loader)) {
            Player placed = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(placed, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the placed racer's placement on the first map");
            loader.hold();
            Player loading = cup.connectRacer();
            Collector<BossBarPacket> loadingBars = cup.connectionOf(loading).trackIncoming(BossBarPacket.class);

            cup.tick(LOBBY_TICKS - cup.ticksPlayed());

            assertThat(cup.session.describe()).as("the phase on the lobby's last tick, with a racer still loading")
                    .contains("phase LOBBY");
            assertThat(PlacementFixture.atSpawn(placed, cup.firstWorld, PlacementFixture.FIRST_SPAWN))
                    .as("the placed racer stays on the spawn while GO is held").isTrue();
            assertThat(loading.getInstance()).as("the loading racer has not been moved").isSameAs(cup.lobby);
            loadingBars.assertAnyMatch(packet -> showsTitle(packet, Messages.waitBossBarWaiting(1)));
        }
    }

    /**
     * A racer who has still not loaded when the hold's limit is reached does not hold GO any longer: GO fires on the tick
     * after {@code HOLD_TICKS} held ticks, and that racer has no run yet.
     */
    @Test
    void goFiresAfterTheMaximumHoldWhileARacerIsStillLoading(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, loader)) {
            Player placed = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(placed, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the placed racer's placement on the first map");
            loader.hold();
            Player loading = cup.connectRacer();

            int goTick = untilGame(cup, GO_BUDGET);

            assertThat(goTick).as("the tick GO fired on").isEqualTo(LOBBY_TICKS + HOLD_TICKS);
            assertThat(cup.runs.of(loading.getUuid())).as("the racer still loading holds no run at GO").isEmpty();
        }
    }

    /**
     * A racer placed after GO, because their chunks arrived after the hold ran out, starts their own run on the tick they
     * are placed. Their time counts from that tick, not from GO.
     */
    @Test
    void aRacerPlacedAfterGoStartsTheirOwnRunWhenPlacedAndIsTimedFromThere(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, loader)) {
            Player placed = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(placed, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the placed racer's placement on the first map");
            loader.hold();
            Player late = cup.connectRacer();
            untilGame(cup, GO_BUDGET);
            cup.tick(5);

            loader.release();
            // The run begins on the tick the placement completes, so wait for that tick and not for the move: a racer
            // moved off the spawn before then never completes the placement.
            cup.tickUntil(() -> cup.runs.of(late.getUuid()).isPresent(), PLACEMENT_BUDGET,
                    "the late racer's run to begin once they are placed");
            assertThat(PlacementFixture.atSpawn(late, cup.firstWorld, PlacementFixture.FIRST_SPAWN))
                    .as("the late racer is placed on the spawn").isTrue();
            late.setFlyingWithElytra(true);
            moveTo(late, PlacementFixture.FRONT_OF_FIRST_RING);
            cup.tick();
            moveTo(late, PlacementFixture.BEHIND_FIRST_RING);
            cup.tick();

            RaceRun run = cup.runs.of(late.getUuid()).orElseThrow();
            assertThat(run.finished()).as("the late racer's run finished").isTrue();
            assertThat(run.timeOnCourse(TIMINGS.race()))
                    .as("the late racer's time, counted from the tick they were placed")
                    .isEqualTo(Duration.ofMillis(100));
        }
    }

    /** A racer who disconnects while GO is held does not hold it: GO fires on the lobby's last tick, as it would without them. */
    @Test
    void aRacerWhoDisconnectsWhileGoIsHeldDoesNotHoldGo(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, loader)) {
            Player placed = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(placed, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the placed racer's placement on the first map");
            loader.hold();
            Player leaver = cup.connectRacer();
            cup.tick(5);

            cup.disconnect(leaver);

            assertThat(untilGame(cup, GO_BUDGET)).as("the tick GO fired on").isEqualTo(LOBBY_TICKS);
        }
    }

    /**
     * The hold applies to the second map's lobby as it does to the first: a racer still loading the second map holds GO
     * there for the full limit.
     */
    @Test
    void theSecondMapsGoIsHeldForARacerWhoIsStillLoadingIt(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, loader)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the racer's placement on the first map");
            cup.untilGameStarts();
            // Connected during the race, so the next placement is the second map's, begun when the first map ends.
            loader.hold();
            cup.connectRacer();
            cup.tickUntil(() -> inMap(cup, 2, "phase LOBBY"), GO_BUDGET, "the second map's lobby");
            int lobbyEntered = cup.ticksPlayed();

            int goTick = untilMapGame(cup, 2, GO_BUDGET);

            assertThat(goTick - lobbyEntered).as("ticks the second map's GO was held for")
                    .isEqualTo(LOBBY_TICKS + HOLD_TICKS);
        }
    }

    /**
     * A racer whose placement has already begun its move is not loading: their chunks are there and the move is under way.
     * GO fires on the lobby's last tick for them, and does not wait for the move to finish.
     */
    @Test
    void aRacerWhoseMoveHasBegunDoesNotHoldGo(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, loader)) {
            Player placed = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(placed, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the placed racer's placement on the first map");
            loader.hold();
            cup.connectRacer();
            cup.tick(LOBBY_TICKS - 2 - cup.ticksPlayed());
            // The move starts on the tick the chunks are read as present, which is one tick before the lobby ends.
            loader.release();

            assertThat(untilGame(cup, GO_BUDGET)).as("the tick GO fired on").isEqualTo(LOBBY_TICKS);
        }
    }

    /**
     * A lobby of no length has no countdown to hold, so GO fires on its first tick even while a racer is still loading, and
     * the racer is placed after it, timed from GO as before.
     */
    @Test
    void aLobbyOfNoLengthFiresGoAtOnceEvenWhileARacerIsStillLoading(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        loader.hold();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, NO_LOBBY, loader)) {
            cup.connectRacer();

            assertThat(untilGame(cup, GO_BUDGET)).as("the tick GO fired on").isEqualTo(1);
        }
    }

    /**
     * A racer placed after a GO that was never held is timed from that GO, as a racer has always been: only a GO held for
     * the racer gives them their own start.
     */
    @Test
    void aRacerPlacedAfterAGoThatWasNotHeldIsTimedFromGo(Env env) throws IOException {
        GatedChunkLoader loader = new GatedChunkLoader();
        loader.hold();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, NO_LOBBY, loader)) {
            Player late = cup.connectRacer();
            untilGame(cup, GO_BUDGET);
            cup.tick(5);

            loader.release();
            cup.tickUntil(() -> cup.runs.of(late.getUuid()).isPresent(), PLACEMENT_BUDGET,
                    "the late racer's run to begin once they are placed");
            late.setFlyingWithElytra(true);
            moveTo(late, PlacementFixture.FRONT_OF_FIRST_RING);
            cup.tick();
            moveTo(late, PlacementFixture.BEHIND_FIRST_RING);
            cup.tick();

            RaceRun run = cup.runs.of(late.getUuid()).orElseThrow();
            assertThat(run.timeOnCourse(TIMINGS.race()))
                    .as("the late racer's time, counted from GO on the first tick")
                    .isEqualTo(PlacementFixture.STEP.multipliedBy(cup.ticksPlayed()));
        }
    }

    /**
     * A racer still gliding out of the map before is not loading: their placement begins when the glide-out ends. A glide-out
     * that runs past the next lobby does not hold the next map's GO, which fires on the lobby's last tick.
     */
    @Test
    void aRacerStillGlidingOutOfTheLastMapDoesNotHoldTheNextMapsGo(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the racer's placement on the first map");
            cup.untilGameStarts();
            // Still flying when the race runs out, so the map's end starts a glide-out that outlasts the second lobby.
            racer.setFlyingWithElytra(true);
            cup.tickUntil(() -> inMap(cup, 1, "phase END"), GO_BUDGET, "the first map's end");
            cup.tickUntil(() -> inMap(cup, 2, "phase LOBBY"), GO_BUDGET, "the second map's lobby");
            int lobbyEntered = cup.ticksPlayed();

            int goTick = untilMapGame(cup, 2, GO_BUDGET);

            assertThat(goTick - lobbyEntered).as("ticks the second map's lobby ran for").isEqualTo(LOBBY_TICKS);
        }
    }

    /** When every racer is placed before the lobby ends, GO fires on the lobby's last tick, exactly as it always has. */
    @Test
    void goFiresOnTheLobbysLastTickWhenEveryRacerIsPlacedInTime(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            cup.connectRacer();
            cup.connectRacer();

            assertThat(untilGame(cup, GO_BUDGET)).as("the tick GO fired on").isEqualTo(LOBBY_TICKS);
        }
    }

    /** Ticks until the first map's GO and returns the tick it fired on, counted from the opening of the cup. */
    private static int untilGame(PlacementFixture cup, int budget) {
        return untilMapGame(cup, 1, budget);
    }

    /** Ticks until {@code map} of the cup is in its GAME phase and returns the tick it began on, from the opening of the cup. */
    private static int untilMapGame(PlacementFixture cup, int map, int budget) {
        cup.tickUntil(() -> inMap(cup, map, "phase GAME"), budget, "GO of map %s".formatted(map));
        return cup.ticksPlayed();
    }

    /** Whether the cup's description places it on {@code map} (one-based) in {@code phase}. */
    private static boolean inMap(PlacementFixture cup, int map, String phase) {
        String description = cup.session.describe();
        return description.contains("map %s/2".formatted(map)) && description.contains(phase);
    }

    /** Whether a boss bar packet sets {@code title}, whether it adds the bar or changes its title. */
    private static boolean showsTitle(BossBarPacket packet, Component title) {
        if (packet.action() instanceof BossBarPacket.AddAction add) {
            return add.title().equals(title);
        }
        if (packet.action() instanceof BossBarPacket.UpdateTitleAction update) {
            return update.title().equals(title);
        }
        return false;
    }

    /** Moves a racer to {@code at}, the way a movement packet would, and waits for the move. */
    private static void moveTo(Player racer, Vec3 at) {
        racer.teleport(new Pos(at.x(), at.y(), at.z())).join();
    }
}
