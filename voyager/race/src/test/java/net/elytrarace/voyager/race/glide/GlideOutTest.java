package net.elytrarace.voyager.race.glide;

import net.elytrarace.voyager.api.math.Vec3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The damped speed of a glide-out after a finish or a time limit: 50 ticks, ending at a quarter of the speed it
 * started with, and never reversing.
 */
class GlideOutTest {

    /** A flight at 1.6 blocks per tick along x: the speed the proposal's gentle-braking scenario names. */
    private static final Vec3 FINISH_VELOCITY = new Vec3(1.6, 0.0, 0.0);

    @Test
    void speedAtTheEndOf50TicksIsAtMostAQuarterOfTheFinishSpeedAndNeverReverses() {
        GlideOut glide = new GlideOut(FINISH_VELOCITY);

        assertThat(glide.velocityAt(GlideOut.TICKS).length())
                .as("speed at the end of the glide-out, from a finish at 1.6 blocks per tick")
                .isLessThanOrEqualTo(0.4);
        for (int tick = 1; tick <= GlideOut.TICKS; tick++) {
            assertThat(glide.velocityAt(tick).dot(FINISH_VELOCITY))
                    .as("direction at tick %s, which must not reverse", tick)
                    .isPositive();
        }
    }

    @Test
    void theSpeedFallsOnEveryTickOfTheGlide() {
        GlideOut glide = new GlideOut(FINISH_VELOCITY);

        for (int tick = 1; tick <= GlideOut.TICKS; tick++) {
            assertThat(glide.velocityAt(tick).length())
                    .as("speed at tick %s is below the speed at tick %s", tick, tick - 1)
                    .isLessThan(glide.velocityAt(tick - 1).length());
        }
    }

    @Test
    void the_glide_is_still_well_above_a_quarter_speed_at_tick_ten() {
        GlideOut glide = new GlideOut(FINISH_VELOCITY);

        assertThat(glide.velocityAt(10).length())
                .as("speed at tick 10 of 50, which must not be a hard stop")
                .isGreaterThan(0.4);
    }

    @Test
    void the_finish_tick_is_the_finish_velocity_itself() {
        GlideOut glide = new GlideOut(FINISH_VELOCITY);

        assertThat(glide.velocityAt(0)).isEqualTo(FINISH_VELOCITY);
    }

    @Test
    void the_glide_has_fifty_ticks() {
        assertThat(GlideOut.TICKS).isEqualTo(50);
    }
}
