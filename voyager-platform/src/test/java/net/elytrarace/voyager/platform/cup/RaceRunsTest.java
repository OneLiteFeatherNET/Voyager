package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.cup.exception.UnstartedRunException;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.run.RaceRun;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RaceRuns} against its own surface, with no server involved — nothing here needs one.
 *
 * <h2>The fixture, and why its numbers are the ones they are</h2>
 *
 * <p>A lane off-axis in {@code x} and {@code y}, a course starting at {@code z = 3} rather than at
 * zero, and tilted ring normals, so a ring's crossing is
 *
 * <pre>
 *   crossing z = ring.cz + (nx * (ring.cx - laneX) + ny * (ring.cy - laneY)) / nz
 *   game tick  = ceil((crossing z - spawn z) / blocksPerTick)
 * </pre>
 *
 * <p>giving movement ticks <strong>5</strong> and <strong>12</strong>. Both differ from the ring
 * indices {@code 0, 1} that produce them and from the ring counts {@code 1, 2} they leave behind, so
 * a board that stored an index or a count where a tick belongs cannot pass.
 *
 * <p>The two players carry unrelated {@link UUID}s rather than ones built from a loop counter: a
 * board keyed by anything other than the id it was handed would still line up if the ids were
 * ordered.
 */
class RaceRunsTest {

    /** One Minecraft tick at 20 TPS. */
    private static final Duration TICK = Duration.ofMillis(50);

    private static final double LANE_X = 1.25;
    private static final double LANE_Y = 65.5;
    private static final double SPAWN_Z = 3.0;
    private static final double BLOCKS_PER_TICK = 5.0;

    private static final Vec3 SPAWN = new Vec3(LANE_X, LANE_Y, SPAWN_Z);

    private static final MapDefinition COURSE = new MapDefinition("ridge-run", "ridge_arena", SPAWN,
            List.of(
                    new Ring(0, new Vec3(0.0, 64.0, 28.0), new Vec3(0.6, 0.0, 0.8), 6.0, 7, RingType.STANDARD),
                    new Ring(1, new Vec3(2.5, 67.0, 61.0), new Vec3(0.0, -0.6, 0.8), 7.0, 11, RingType.BOOST)),
            Duration.ofSeconds(4), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.25));

    private static final int FIRST_RING_TICK = 5;
    private static final int LAST_RING_TICK = 12;

    private static final UUID FLYER = UUID.fromString("3f1c9a42-7b0e-4d86-9c11-2ae5d0f7b318");
    private static final UUID TRAILER = UUID.fromString("c07d6e15-2f93-40ab-8d54-61b7ce29a4f0");

    // ------------------------------------------------------------------------------------------
    // Holding a run
    // ------------------------------------------------------------------------------------------

    @Test
    void aPlayerWhoWasNeverMovedToThisMapHoldsNoRun() {
        RaceRuns runs = new RaceRuns();

        assertThat(runs.of(FLYER)).isEmpty();
    }

    @Test
    void startingFreshGivesAPlayerARunAtTheStartOfTheMap() {
        RaceRuns runs = new RaceRuns();

        runs.startFresh(FLYER);

        assertThat(runs.of(FLYER)).contains(RaceRun.atStart());
        // And only that player: the entry is keyed, not shared.
        assertThat(runs.of(TRAILER)).isEmpty();
    }

    /**
     * The run {@code advance} returns has to be the run the board now holds. A board that advanced a
     * copy and kept the old one would look right to the caller of that single call and lose every
     * ring on the next tick, because {@code previous} would never move off the spawn.
     */
    @Test
    void advancingStoresTheRunItReturnsSoTheNextTickContinuesFromIt() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);

        RaceRun returned = fly(runs, FLYER, FIRST_RING_TICK);

        assertThat(returned.passedOnGameTick()).containsExactly(FIRST_RING_TICK);
        assertThat(runs.of(FLYER)).contains(returned);
        assertThat(runs.of(FLYER).orElseThrow().previous()).isEqualTo(positionAt(FIRST_RING_TICK));
    }

    @Test
    void eachPlayersRunIsAdvancedOnItsOwn() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        runs.startFresh(TRAILER);

        fly(runs, FLYER, LAST_RING_TICK);
        fly(runs, TRAILER, FIRST_RING_TICK);

        assertThat(runs.of(FLYER).orElseThrow().passedOnGameTick())
                .containsExactly(FIRST_RING_TICK, LAST_RING_TICK);
        assertThat(runs.of(TRAILER).orElseThrow().passedOnGameTick())
                .containsExactly(FIRST_RING_TICK);
    }

    /**
     * The gliding flag is handed through rather than assumed. Flown on foot the same lane passes
     * nothing at all, so a board that hard-coded {@code true} would still record ring 0 on tick 5.
     */
    @Test
    void theGlidingFlagReachesTheRunSoARingIsNotPassedOnFoot() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);

        RaceClock clock = RaceClock.startingAt(TICK);
        for (int tick = 1; tick <= LAST_RING_TICK; tick++) {
            clock = clock.advanced();
            runs.advance(FLYER, COURSE, clock, positionAt(tick), false);
        }

        assertThat(runs.of(FLYER).orElseThrow().passedOnGameTick()).isEmpty();
    }

    @Test
    void advancingAPlayerWhoHoldsNoRunNamesThatPlayerAndSaysWhyThereIsNone() {
        RaceRuns runs = new RaceRuns();
        RaceClock clock = RaceClock.startingAt(TICK).advanced();

        assertThatThrownBy(() -> runs.advance(FLYER, COURSE, clock, positionAt(1), true))
                .isInstanceOf(UnstartedRunException.class)
                .hasMessageContaining(FLYER.toString())
                .hasMessageContaining("moving the player to that map's spawn");
    }

    // ------------------------------------------------------------------------------------------
    // Losing a run
    // ------------------------------------------------------------------------------------------

    @Test
    void clearingDropsEveryRun() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        runs.startFresh(TRAILER);
        fly(runs, FLYER, FIRST_RING_TICK);

        runs.clearAll();

        assertThat(runs.of(FLYER)).isEmpty();
        assertThat(runs.of(TRAILER)).isEmpty();
    }

    /**
     * A cleared board does not remember what the dropped run had reached: the next map's first tick
     * starts from nothing, not from the previous map's ring count.
     */
    @Test
    void aRunStartedAfterAClearBeginsFromNothing() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        fly(runs, FLYER, LAST_RING_TICK);

        runs.clearAll();
        runs.startFresh(FLYER);

        assertThat(runs.of(FLYER)).contains(RaceRun.atStart());
    }

    @Test
    void forgettingDropsOnlyThatPlayersRun() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        runs.startFresh(TRAILER);
        fly(runs, TRAILER, FIRST_RING_TICK);

        runs.forget(FLYER);

        assertThat(runs.of(FLYER)).isEmpty();
        assertThat(runs.of(TRAILER).orElseThrow().passedOnGameTick()).containsExactly(FIRST_RING_TICK);
    }

    @Test
    void forgettingAPlayerWhoHoldsNoRunIsNotAnError() {
        RaceRuns runs = new RaceRuns();

        runs.forget(FLYER);

        assertThat(runs.of(FLYER)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Ending the phase early
    // ------------------------------------------------------------------------------------------

    /**
     * The vacuous-truth trap, and the reason it is worth a test of its own: {@code allMatch} over an
     * empty board answers {@code true}, and a {@code GAME} phase reading that would end on its first
     * tick — before the transition has filled the board, and again the moment the last racer
     * disconnects.
     */
    @Test
    void aMapNobodyIsRacingIsNotWonByEverybody() {
        RaceRuns runs = new RaceRuns();

        assertThat(runs.everyRacerFinished()).isFalse();
    }

    @Test
    void theMapIsNotOverWhileOneRacerIsStillOnTheCourse() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        runs.startFresh(TRAILER);

        fly(runs, FLYER, LAST_RING_TICK);
        fly(runs, TRAILER, FIRST_RING_TICK);

        assertThat(runs.of(FLYER).orElseThrow().finished()).isTrue();
        assertThat(runs.of(TRAILER).orElseThrow().finished()).isFalse();
        assertThat(runs.everyRacerFinished()).isFalse();
    }

    @Test
    void theMapIsOverOnceEveryRacerHasPassedTheLastRing() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        runs.startFresh(TRAILER);

        fly(runs, FLYER, LAST_RING_TICK);
        fly(runs, TRAILER, LAST_RING_TICK);

        assertThat(runs.everyRacerFinished()).isTrue();
    }

    /**
     * The leak {@link RaceRuns#forget(UUID)} closes, with the symptom it has when it is skipped: a
     * racer who disconnected mid-course never finishes, so the phase waits out its whole time limit
     * for somebody who is not on the server.
     */
    @Test
    void aDisconnectedRacerNoLongerHoldsTheMapOpen() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        runs.startFresh(TRAILER);
        fly(runs, FLYER, LAST_RING_TICK);
        fly(runs, TRAILER, FIRST_RING_TICK);

        runs.forget(TRAILER);

        assertThat(runs.everyRacerFinished()).isTrue();
    }

    @Test
    void everyRacerLeavingDoesNotCountAsEveryRacerFinishing() {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(FLYER);
        fly(runs, FLYER, FIRST_RING_TICK);

        runs.forget(FLYER);

        assertThat(runs.everyRacerFinished()).isFalse();
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    private static RaceRun fly(RaceRuns runs, UUID playerId, int throughTick) {
        RaceClock clock = RaceClock.startingAt(TICK);
        RaceRun flown = runs.of(playerId).orElseThrow();
        for (int tick = 1; tick <= throughTick; tick++) {
            clock = clock.advanced();
            flown = runs.advance(playerId, COURSE, clock, positionAt(tick), true);
        }
        return flown;
    }

    private static Vec3 positionAt(int gameTick) {
        return new Vec3(LANE_X, LANE_Y, SPAWN_Z + BLOCKS_PER_TICK * gameTick);
    }
}
