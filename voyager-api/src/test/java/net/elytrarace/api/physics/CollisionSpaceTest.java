package net.elytrarace.api.physics;

import net.elytrarace.api.math.Aabb;
import net.elytrarace.api.math.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollisionSpaceTest {

    private static final Aabb UNIT = new Aabb(Vec3.ZERO, new Vec3(1, 1, 1));

    @Test
    void emptySpaceReturnsNoBoxes() {
        assertThat(CollisionSpace.empty().boxesIntersecting(UNIT)).isEmpty();
    }

    @Test
    void isAFunctionalInterfaceSoTestsCanSupplyAWorld() {
        CollisionSpace floor = region -> List.of(new Aabb(new Vec3(-8, -1, -8), new Vec3(8, 0, 8)));

        assertThat(floor.boxesIntersecting(UNIT)).hasSize(1);
    }
}
