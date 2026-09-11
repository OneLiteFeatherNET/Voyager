package net.elytrarace.voyager.race.effect;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffect;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RingEffectRegistryTest {

    // Mutually distinct, not-all-positive components: (1, 1, 1) can't tell a multiplier from a
    // component swap, and all-positive components can't reveal a lost sign.
    private static final Vec3 VELOCITY = new Vec3(0.75, -0.32, 1.6);

    private static final double TOLERANCE = 1e-12;

    private final RingEffectRegistry registry = RingEffectRegistry.create();

    @Test
    void boostScalesEveryComponentByOneAndAHalf() {
        RingEffect boost = registry.effectFor(RingType.BOOST).orElseThrow();

        Vec3 result = boost.apply(VELOCITY);

        assertThat(result.x()).isCloseTo(1.125, within(TOLERANCE));
        assertThat(result.y()).isCloseTo(-0.48, within(TOLERANCE));
        assertThat(result.z()).isCloseTo(2.4, within(TOLERANCE));
    }

    @Test
    void slowScalesEveryComponentByOneHalf() {
        RingEffect slow = registry.effectFor(RingType.SLOW).orElseThrow();

        Vec3 result = slow.apply(VELOCITY);

        assertThat(result.x()).isCloseTo(0.375, within(TOLERANCE));
        assertThat(result.y()).isCloseTo(-0.16, within(TOLERANCE));
        assertThat(result.z()).isCloseTo(0.8, within(TOLERANCE));
    }

    @Test
    void standardHasNoEffectToApply() {
        assertThat(registry.effectFor(RingType.STANDARD)).isNotNull().isEmpty();
    }

    @Test
    void checkpointHasNoEffectToApply() {
        assertThat(registry.effectFor(RingType.CHECKPOINT)).isNotNull().isEmpty();
    }

    @Test
    void zeroVelocityStaysZeroUnderBoostAndSlow() {
        RingEffect boost = registry.effectFor(RingType.BOOST).orElseThrow();
        RingEffect slow = registry.effectFor(RingType.SLOW).orElseThrow();

        assertThat(boost.apply(Vec3.ZERO)).isEqualTo(Vec3.ZERO);
        assertThat(slow.apply(Vec3.ZERO)).isEqualTo(Vec3.ZERO);
    }

    @Test
    void effectForNeverReturnsNullEvenWhenEmpty() {
        Optional<RingEffect> unmapped = registry.effectFor(RingType.STANDARD);

        assertThat(unmapped).isNotNull();
    }

    @Test
    void entriesViewCannotBeMutated() {
        Map<RingType, RingEffect> entries = registry.entries();

        assertThatThrownBy(() -> entries.put(RingType.STANDARD, boostEffect()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> entries.remove(RingType.BOOST))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Two registries share nothing a caller could use to change one through the other: the mapping
     * is built once and immutable, so the absence of a {@code register} is not something a caller
     * can route around by holding on to {@code entries()}.
     */
    @Test
    void twoRegistriesAgreeOnEveryRingTypeAndNeitherCanBeChanged() {
        RingEffectRegistry other = RingEffectRegistry.create();

        for (RingType type : RingType.values()) {
            assertThat(registry.effectFor(type).isPresent())
                    .as("both registries map %s the same way".formatted(type))
                    .isEqualTo(other.effectFor(type).isPresent());
        }
        assertThatThrownBy(() -> other.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entriesContainsExactlyBoostAndSlow() {
        Map<RingType, RingEffect> entries = registry.entries();

        assertThat(entries).containsOnlyKeys(RingType.BOOST, RingType.SLOW);
    }

    private RingEffect boostEffect() {
        return registry.effectFor(RingType.BOOST).orElseThrow();
    }
}
