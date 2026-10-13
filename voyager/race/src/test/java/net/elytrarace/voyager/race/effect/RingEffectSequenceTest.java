package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.effect.BaseRingEffect;
import net.elytrarace.voyager.api.race.effect.RingEffect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RingEffectSequenceTest {

    private static final double TOLERANCE = 1e-12;

    private static final RingEffect BOOST = new SpeedMultiplierEffect(1.5);
    private static final RingEffect SLOW = new SpeedMultiplierEffect(0.5);

    /** Adds one block per tick along z. Not a multiplier, so the order it is applied in is visible in the result. */
    private static final RingEffect PLUS_ONE = new BaseRingEffect() {
        @Override
        public Vec3 apply(Vec3 velocity) {
            return velocity.plus(new Vec3(0.0, 0.0, 1.0));
        }
    };

    @Test
    void boostThenSlowLeavesThreeQuartersOfTheSpeed() {
        Vec3 result = RingEffectSequence.apply(new Vec3(0.0, 0.0, 2.0), List.of(BOOST, SLOW), null);

        assertThat(result.z()).isCloseTo(1.5, within(TOLERANCE));
    }

    @Test
    void twoBoostRingsGiveTwoAndAQuarterTimesTheSpeedWithNoCap() {
        Vec3 result = RingEffectSequence.apply(new Vec3(0.0, 0.0, 2.0), List.of(BOOST, BOOST), null);

        assertThat(result.z()).isCloseTo(4.5, within(TOLERANCE));
    }

    @Test
    void twoBoostRingsAreCappedOnceWhenACapIsStated() {
        Vec3 result = RingEffectSequence.apply(new Vec3(0.0, 0.0, 2.0), List.of(BOOST, BOOST), 3.5);

        assertThat(result.z()).isCloseTo(3.5, within(TOLERANCE));
    }

    /** Effects compose in the order the rings were crossed: (1 times 2) plus one is 3, and (1 plus one) times 2 is 4. */
    @Test
    void effectsComposeInCrossingOrder() {
        SpeedMultiplierEffect doubling = new SpeedMultiplierEffect(2.0);
        Vec3 start = new Vec3(0.0, 0.0, 1.0);

        Vec3 doubledThenPlus = RingEffectSequence.apply(start, List.of(doubling, PLUS_ONE), null);
        Vec3 plusThenDoubled = RingEffectSequence.apply(start, List.of(PLUS_ONE, doubling), null);

        assertThat(doubledThenPlus.z()).isCloseTo(3.0, within(TOLERANCE));
        assertThat(plusThenDoubled.z()).isCloseTo(4.0, within(TOLERANCE));
    }

    /** No effect means no change, so the cap, which limits what an effect writes, does not touch it. */
    @Test
    void noEffectLeavesTheVelocityUnchangedEvenAboveACap() {
        Vec3 velocity = new Vec3(0.0, 0.0, 9.0);

        assertThat(RingEffectSequence.apply(velocity, List.of(), 3.5)).isEqualTo(velocity);
    }
}
