package net.elytrarace.api.math;

import net.elytrarace.api.math.exception.InvalidBoundingBoxException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AabbTest {

    private static Aabb box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new Aabb(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    @Test
    void rejectsInvertedBounds() {
        assertThatThrownBy(() -> box(1, 0, 0, 0, 1, 1)).isInstanceOf(InvalidBoundingBoxException.class);
        assertThatThrownBy(() -> box(0, 1, 0, 1, 0, 1)).isInstanceOf(InvalidBoundingBoxException.class);
        assertThatThrownBy(() -> box(0, 0, 1, 1, 1, 0)).isInstanceOf(InvalidBoundingBoxException.class);
    }

    @Test
    void acceptsDegenerateBoundsWhereMinEqualsMax() {
        assertThat(box(1, 1, 1, 1, 1, 1)).isNotNull();
    }

    @Test
    void detectsOverlap() {
        assertThat(box(0, 0, 0, 2, 2, 2).intersects(box(1, 1, 1, 3, 3, 3))).isTrue();
    }

    @Test
    void treatsTouchingFacesAsIntersecting() {
        assertThat(box(0, 0, 0, 1, 1, 1).intersects(box(1, 0, 0, 2, 1, 1))).isTrue();
    }

    @Test
    void detectsSeparationOnASingleAxis() {
        assertThat(box(0, 0, 0, 1, 1, 1).intersects(box(2, 0, 0, 3, 1, 1))).isFalse();
    }

    @Test
    void expandsSymmetrically() {
        assertThat(box(0, 0, 0, 1, 1, 1).expand(0.5)).isEqualTo(box(-0.5, -0.5, -0.5, 1.5, 1.5, 1.5));
    }
}
