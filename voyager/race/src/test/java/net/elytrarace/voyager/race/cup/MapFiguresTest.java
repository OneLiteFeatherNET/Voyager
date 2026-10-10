package net.elytrarace.voyager.race.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.MedalCountdown;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The figures a racer's HUD shows during a map, decided by the race rather than by the platform that draws them. */
class MapFiguresTest {

    private static final Duration STEP = Duration.ofMillis(50);

    private static final MapDefinition MAP = new MapDefinition("two-ring", "world_a", Vec3.ZERO,
            List.of(ring(0, 9), ring(1, 25)), Duration.ofSeconds(60), new BoostConfig(12, 25),
            new GuideLine(List.of(), 2, 1.0));

    @Test
    void inFlightShowsTheClockAFinisherCrossedTheLineOnRatherThanTheRunningOne() {
        RaceClock finishedOn = new RaceClock(8, STEP);
        RaceRun finished = new RaceRun(new RingProgress(2, 1), null, List.of(4, 8), Optional.of(finishedOn), null);

        MapFigures figures = MapFigures.inFlight(finished, new RaceClock(30, STEP), MAP, 1, 2);

        assertThat(figures.elapsed())
                .describedAs("a racer who has finished is shown the time they finished on")
                .isEqualTo(finishedOn.elapsed());
        assertThat(figures.gameTick()).describedAs("the tick is still the current one").isEqualTo(30);
    }

    @Test
    void inFlightShowsTheRunningClockForARacerWhoIsStillRacing() {
        RaceClock now = new RaceClock(30, STEP);
        RaceRun racing = new RaceRun(new RingProgress(1, 0), null, List.of(4), Optional.empty(), null);

        MapFigures figures = MapFigures.inFlight(racing, now, MAP, 1, 2);

        assertThat(figures.elapsed()).isEqualTo(now.elapsed());
    }

    @Test
    void lastRingTickIsTheTickOfTheMostRecentlyPassedRing() {
        RaceRun racing = new RaceRun(new RingProgress(2, 1), null, List.of(4, 8), Optional.empty(), null);

        MapFigures figures = MapFigures.inFlight(racing, new RaceClock(9, STEP), MAP, 1, 2);

        assertThat(figures.lastRingTick()).isEqualTo(8);
        assertThat(figures.ringsPassed()).isEqualTo(2);
    }

    @Test
    void lastRingTickIsZeroBeforeAnyRingIsPassed() {
        RaceRun notYet = RaceRun.atStart();

        MapFigures figures = MapFigures.inFlight(notYet, new RaceClock(3, STEP), MAP, 1, 2);

        assertThat(figures.lastRingTick()).isZero();
        assertThat(figures.ringsPassed()).isZero();
    }

    @Test
    void startingShowsNothingFlownAndTheMedalOutlookAtZeroElapsed() {
        MapFigures figures = MapFigures.starting(MAP, 1, 2);

        assertThat(figures.gameTick()).isZero();
        assertThat(figures.elapsed()).isZero();
        assertThat(figures.ringsPassed()).isZero();
        assertThat(figures.lastRingTick()).isZero();
        assertThat(figures.ringCount()).isEqualTo(2);
        assertThat(figures.mapNumber()).isEqualTo(1);
        assertThat(figures.mapCount()).isEqualTo(2);
        assertThat(figures.mapName()).isEqualTo("two-ring");
        assertThat(figures.outlook())
                .isEqualTo(MedalCountdown.outlook(Duration.ZERO, MAP.referenceTime(), MedalBrackets.DEFAULT));
    }

    private static Ring ring(int index, int points) {
        return new Ring(index, new Vec3(0, 64, 10 * (index + 1)), new Vec3(0, 0, 1), 6.0, points, RingType.STANDARD);
    }
}
