package net.elytrarace.api.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class Vec3Test {

    @Test
    void acceptsFiniteComponents() {
        Vec3 vec = new Vec3(1.5, -2.25, 3.0);

        assertThat(vec.x()).isEqualTo(1.5);
        assertThat(vec.y()).isEqualTo(-2.25);
        assertThat(vec.z()).isEqualTo(3.0);
    }

    @Test
    void rejectsNaNInAnyComponent() {
        assertThatThrownBy(() -> new Vec3(Double.NaN, 0, 0)).isInstanceOf(NonFiniteVectorException.class);
        assertThatThrownBy(() -> new Vec3(0, Double.NaN, 0)).isInstanceOf(NonFiniteVectorException.class);
        assertThatThrownBy(() -> new Vec3(0, 0, Double.NaN)).isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void rejectsInfinityInAnyComponent() {
        assertThatThrownBy(() -> new Vec3(Double.POSITIVE_INFINITY, 0, 0))
                .isInstanceOf(NonFiniteVectorException.class);
        assertThatThrownBy(() -> new Vec3(0, Double.NEGATIVE_INFINITY, 0))
                .isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void arithmeticThatOverflowsToInfinityIsRejectedAtConstruction() {
        Vec3 huge = new Vec3(Double.MAX_VALUE, 0, 0);

        assertThatThrownBy(() -> huge.scale(2.0))
                .as("overflow must not silently produce an infinite vector")
                .isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void addsAndSubtractsComponentwise() {
        Vec3 a = new Vec3(1, 2, 3);
        Vec3 b = new Vec3(0.5, -1, 2);

        assertThat(a.plus(b)).isEqualTo(new Vec3(1.5, 1, 5));
        assertThat(a.minus(b)).isEqualTo(new Vec3(0.5, 3, 1));
    }

    @Test
    void computesLength() {
        Vec3 vec = new Vec3(3, 4, 0);

        assertThat(vec.lengthSquared()).isEqualTo(25.0);
        assertThat(vec.length()).isCloseTo(5.0, within(1e-12));
    }

    @Test
    void exposesAZeroConstant() {
        assertThat(Vec3.ZERO).isEqualTo(new Vec3(0, 0, 0));
    }
}
