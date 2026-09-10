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

    @Test
    void squareMultipliesAValueByItself() {
        assertThat(MinecraftMath.square(1.5)).isEqualTo(2.25);
    }
}
