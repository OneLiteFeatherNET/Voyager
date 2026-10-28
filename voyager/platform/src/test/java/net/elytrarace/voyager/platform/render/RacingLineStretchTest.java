package net.elytrarace.voyager.platform.render;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideLineMode;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.line.RacingLine;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which stretch of the racing line a racer is shown, measured from the last ring they passed.
 *
 * <p>The segment a racer is flying lies between the last ring passed and the ring they head for. It is drawn
 * until the next ring is passed, so the stretch starts one ring behind the ring being headed for.
 */
class RacingLineStretchTest {

    private static final List<Ring> RINGS = List.of(
            ring(0, new Vec3(0.0, 64.0, 0.0)),
            ring(1, new Vec3(0.0, 70.0, 60.0)),
            ring(2, new Vec3(40.0, 80.0, 110.0)),
            ring(3, new Vec3(40.0, 90.0, 170.0)),
            ring(4, new Vec3(0.0, 100.0, 220.0)));

    private static final GuidePoint AROUND_THE_CLIFF = new GuidePoint(150, new Vec3(20.0, 76.0, 80.0));

    private static final RacingLine LINE = RacingLine.of(course(new GuideLine(List.of(AROUND_THE_CLIFF), 2, 5.0)));

    /**
     * A racer who has passed ring 1 is heading for ring 2, and the segment between them is still flown. The stretch
     * starts at ring 1, and nothing behind it is drawn.
     */
    @Test
    void theStretchStartsAtTheLastRingPassedSoTheSegmentBeingFlownStaysDrawn() {
        List<Vec3> stretch = GuideLineRenderer.stretch(LINE, 2, GuideLineRenderer.lookAheadOf(
                new GuideLine(List.of(AROUND_THE_CLIFF), 2, 5.0)));

        assertThat(stretch.getFirst()).as("first point of the full stretch").isEqualTo(RINGS.get(1).center());
        assertThat(stretch).as("points behind the last ring passed").doesNotContain(RINGS.get(0).center());
    }

    /**
     * A window of one ring past the ring being headed for: a racer who has passed ring 1 and heads for ring 2 is
     * shown from ring 1 to ring 3, and nothing beyond.
     */
    @Test
    void aWindowStretchStartsAtTheLastRingPassedAndReachesLookAheadRingsPastTheRingBeingHeadedFor() {
        GuideLine window = new GuideLine(List.of(AROUND_THE_CLIFF), 1, 5.0, GuideLineMode.WINDOW,
                GuideParticle.END_ROD, null);
        List<Vec3> stretch = GuideLineRenderer.stretch(LINE, 2, GuideLineRenderer.lookAheadOf(window));

        assertThat(stretch.getFirst()).as("first point of the window").isEqualTo(RINGS.get(1).center());
        assertThat(stretch.getLast()).as("last point of the window").isEqualTo(RINGS.get(3).center());
        assertThat(stretch).as("points past the window").doesNotContain(RINGS.get(4).center());
    }

    private static MapDefinition course(GuideLine style) {
        return new MapDefinition("ridge-run", "ridge_arena", new Vec3(0.5, 64.0, -10.0), RINGS,
                Duration.ofSeconds(30), new BoostConfig(12, 25), style);
    }

    private static Ring ring(int index, Vec3 center) {
        return new Ring(index, center, new Vec3(0.0, 0.0, 1.0), 5.0, 10, RingType.STANDARD);
    }
}
