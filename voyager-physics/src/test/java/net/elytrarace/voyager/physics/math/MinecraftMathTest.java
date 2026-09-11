package net.elytrarace.voyager.physics.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MinecraftMathTest {

    @Test
    void degToRadIsTheFloatQuotientVanillaUses() {
        assertThat(MinecraftMath.DEG_TO_RAD).isEqualTo((float) (Math.PI / 180.0));
    }

    @Test
    void sinAndCosAreTableLookupsAndNotTheJdkFunctions() {
        // The point of this test: a port that swapped in Math.sin would pass everything else here.
        // 0.3 radians is not a table index boundary, so the quantised value must differ.
        assertThat(MinecraftMath.sin(0.3)).isNotEqualTo((float) Math.sin(0.3));
        assertThat(MinecraftMath.cos(0.3)).isNotEqualTo((float) Math.cos(0.3));
    }

    @Test
    void sinStaysCloseToTheRealFunction() {
        for (double radians = -Math.PI; radians < Math.PI; radians += 0.017) {
            assertThat((double) MinecraftMath.sin(radians))
                    .as("sin(%s)".formatted(radians))
                    .isCloseTo(Math.sin(radians), within(1.0e-3));
        }
    }

    @Test
    void cosStaysCloseToTheRealFunction() {
        for (double radians = -Math.PI; radians < Math.PI; radians += 0.017) {
            assertThat((double) MinecraftMath.cos(radians))
                    .as("cos(%s)".formatted(radians))
                    .isCloseTo(Math.cos(radians), within(1.0e-3));
        }
    }

    @Test
    void reproducesTheTableExactlyAtAnIndexBoundary() {
        // Index 1000 of a 65536-entry table built as sin[i] = (float) Math.sin(i / 10430.378350470453).
        double atIndexOneThousand = 1000 / 10430.378350470453;

        assertThat(MinecraftMath.sin(atIndexOneThousand)).isEqualTo((float) Math.sin(atIndexOneThousand));
    }

    @Test
    void wrapsAroundRatherThanFailingOnLargeAngles() {
        assertThat(MinecraftMath.sin(1000.0)).isBetween(-1.0f, 1.0f);
        assertThat(MinecraftMath.cos(-1000.0)).isBetween(-1.0f, 1.0f);
    }

    @Test
    void cosIsSinShiftedByAQuarterTurn() {
        assertThat(MinecraftMath.cos(0.0)).isEqualTo(MinecraftMath.sin(Math.PI / 2.0));
    }

    // --- Fix-Runde 2: the index arithmetic itself, which nothing above pinned. ---

    /**
     * {@code Mth.cos} had no reference authority in {@code docs/reference/elytra-physics-26.2.md}
     * and no test that pinned which table entry it picks. The index is computed in {@code double}
     * and truncated through a {@code long}; the "classic float form"
     * ({@code (int) (radians * 10430.378F + 16384.0F) & 65535}) that a reader might assume Vanilla
     * uses picks a neighbouring entry for roughly 0.1% of angles. 26.2 has no {@code float} overload
     * of {@code Mth.cos} at all, so {@code calculateViewVector}'s {@code float} arguments widen to
     * {@code double} and bind to the form transcribed here.
     *
     * <p>{@code -1.56955} is one such angle: the double form lands on index 12, the float form on
     * index 13, and the two entries are {@code 9.59e-5} apart — a full table step, a hundred times
     * the {@code 1e-6} per-tick parity threshold. The expected value is derived from the table's own
     * definition ({@code SIN[i] = (float) Math.sin(i / 10430.378350470453)}) at the index the double
     * arithmetic selects, not read back out of {@link MinecraftMath}.
     */
    @Test
    void cosSelectsItsTableEntryWithDoubleNotFloatIndexArithmetic() {
        double radians = -1.56955;
        int doubleFormIndex = (int) ((long) (radians * 10430.378350470453 + 16384.0) & 65535L);
        int floatFormIndex = (int) ((float) radians * 10430.378F + 16384.0F) & 65535;

        assertThat(doubleFormIndex).isEqualTo(12);
        assertThat(floatFormIndex).isEqualTo(13);
        assertThat(MinecraftMath.cos(radians))
                .isEqualTo((float) Math.sin(doubleFormIndex / 10430.378350470453));
        assertThat(MinecraftMath.cos(radians))
                .isNotEqualTo((float) Math.sin(floatFormIndex / 10430.378350470453));
    }

    /**
     * The {@code Mth.sin} counterpart of
     * {@link #cosSelectsItsTableEntryWithDoubleNotFloatIndexArithmetic}, differing only in the
     * missing {@code 16384.0} quarter-turn offset. At {@code -3.11935} the double form lands on
     * index 33000 and the float form on 33001, {@code 9.58e-5} apart.
     */
    @Test
    void sinSelectsItsTableEntryWithDoubleNotFloatIndexArithmetic() {
        double radians = -3.11935;
        int doubleFormIndex = (int) ((long) (radians * 10430.378350470453) & 65535L);
        int floatFormIndex = (int) ((float) radians * 10430.378F) & 65535;

        assertThat(doubleFormIndex).isEqualTo(33000);
        assertThat(floatFormIndex).isEqualTo(33001);
        assertThat(MinecraftMath.sin(radians))
                .isEqualTo((float) Math.sin(doubleFormIndex / 10430.378350470453));
        assertThat(MinecraftMath.sin(radians))
                .isNotEqualTo((float) Math.sin(floatFormIndex / 10430.378350470453));
    }

    @Test
    void squareMultipliesAValueByItself() {
        assertThat(MinecraftMath.square(1.5)).isEqualTo(2.25);
    }
}
