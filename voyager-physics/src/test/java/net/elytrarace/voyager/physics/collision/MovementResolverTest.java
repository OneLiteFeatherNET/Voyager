package net.elytrarace.voyager.physics.collision;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MovementResolverTest {

    /** A 0.6 x 1.8 box, the shape of a standing player, with its feet at the given point. */
    private static Aabb boxAt(double x, double y, double z) {
        return new Aabb(new Vec3(x - 0.3, y, z - 0.3), new Vec3(x + 0.3, y + 1.8, z + 0.3));
    }

    private static CollisionSpace floorAtZero() {
        return region -> List.of(new Aabb(new Vec3(-64, -1, -64), new Vec3(64, 0, 64)));
    }

    private static CollisionSpace wallAtXFive() {
        return region -> List.of(new Aabb(new Vec3(5, -64, -64), new Vec3(6, 64, 64)));
    }

    @Test
    void emptySpaceAllowsTheWholeMovement() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(1, -0.5, 2), CollisionSpace.empty());

        assertThat(result.allowedMovement()).isEqualTo(new Vec3(1, -0.5, 2));
        assertThat(result.horizontalCollision()).isFalse();
        assertThat(result.onGround()).isFalse();
    }

    @Test
    void aFloorStopsDownwardMovementExactlyAtItsSurface() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0.5, 0), new Vec3(0, -2.0, 0), floorAtZero());

        assertThat(result.allowedMovement().y()).isCloseTo(-0.5, within(1.0e-9));
        assertThat(result.onGround()).isTrue();
    }

    @Test
    void landingDoesNotReportAHorizontalCollision() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0.5, 0), new Vec3(0, -2.0, 0), floorAtZero());

        assertThat(result.horizontalCollision()).isFalse();
    }

    @Test
    void aWallStopsHorizontalMovementAndReportsIt() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(10.0, 0, 0), wallAtXFive());

        assertThat(result.allowedMovement().x()).isCloseTo(4.7, within(1.0e-9));
        assertThat(result.horizontalCollision()).isTrue();
        assertThat(result.onGround()).isFalse();
    }

    @Test
    void anAxisWithoutAnObstacleIsUnaffectedByOneThatHasOne() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(10.0, 0, 3.0), wallAtXFive());

        assertThat(result.allowedMovement().z()).isEqualTo(3.0);
    }

    @Test
    void risingIntoNothingIsNotGrounded() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0.5, 0), new Vec3(0, 2.0, 0), floorAtZero());

        assertThat(result.allowedMovement().y()).isEqualTo(2.0);
        assertThat(result.onGround()).isFalse();
    }

    @Test
    void aBoxAlreadyRestingOnTheFloorCannotSinkIntoIt() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0, 0), new Vec3(0, -0.5, 0), floorAtZero());

        assertThat(result.allowedMovement().y()).isCloseTo(0.0, within(1.0e-9));
        assertThat(result.onGround()).isTrue();
    }

    @Test
    void zeroMovementResolvesToZeroWithoutQueryingTheSpace() {
        CollisionSpace exploding = region -> {
            throw new AssertionError("the resolver must not query the space for a zero movement");
        };

        assertThat(MovementResolver.resolve(boxAt(0, 10, 0), Vec3.ZERO, exploding).allowedMovement())
                .isEqualTo(Vec3.ZERO);
    }
}
