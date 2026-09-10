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

    /** A finite step: x in [1, 2], y in [0, 1], unbounded on z. */
    private static CollisionSpace stepAtXOneToTwo() {
        return region -> List.of(new Aabb(new Vec3(1, 0, -64), new Vec3(2, 1, 64)));
    }

    /** A ceiling: y in [7, 8], unbounded on x and z. */
    private static CollisionSpace ceilingAtSeven() {
        return region -> List.of(new Aabb(new Vec3(-64, 7, -64), new Vec3(64, 8, 64)));
    }

    /** A pillar: x in [1, 2] and z in [1, 2], unbounded on y. */
    private static CollisionSpace pillarAtXOneTwoZOneTwo() {
        return region -> List.of(new Aabb(new Vec3(1, -64, 1), new Vec3(2, 64, 2)));
    }

    /**
     * A space that only returns a block when the queried region actually reaches it — unlike the
     * fixtures above, which return their block unconditionally regardless of {@code region}. Used
     * where the swept region (and its epsilon) must be load-bearing for the test to mean anything:
     * against an unconditional fixture, a resolver that never swept the path at all — querying only
     * the box's own footprint — would still find the block and pass.
     */
    private static CollisionSpace filtering(Aabb... blocks) {
        List<Aabb> all = List.of(blocks);
        return region -> all.stream().filter(region::intersects).toList();
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

    /**
     * Uses {@link #filtering}, not {@link #wallAtXFive}: the box starts at x = -0.3..0.3, nowhere
     * near the wall at x = 5..6, so this only finds the wall because the resolver queries the region
     * the box <em>sweeps through</em> while moving — not merely the region it already occupies. A
     * resolver that queried only the box's starting footprint would find nothing here and sail
     * through the wall.
     */
    @Test
    void aWallStopsHorizontalMovementAndReportsIt() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(10.0, 0, 0),
                filtering(new Aabb(new Vec3(5, -64, -64), new Vec3(6, 64, 64))));

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

    /**
     * A finite step forces Y to resolve before X: moving Y first clears the step's x-range before X
     * is ever checked against it (free fall to y = 0.8, then clamped on X to 0.2); resolving X first
     * would instead clear the step's y-range while still above it (free slide of 0.6), then clamp Y
     * against the step's top afterwards (to -0.1). The two orders disagree on both components.
     */
    @Test
    void yIsResolvedBeforeXSoAStepClampsTheFallNotTheSlide() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0.5, 1.1, 0), new Vec3(0.6, -0.3, 0), stepAtXOneToTwo());

        assertThat(result.allowedMovement().x()).isCloseTo(0.2, within(1.0e-9));
        assertThat(result.allowedMovement().y()).isCloseTo(-0.3, within(1.0e-9));
    }

    /**
     * The box's leading x-face sits exactly on the step's x-face (0.4..1.0 against 1..2). Strict
     * overlap, matching {@link Aabb#intersects(Aabb)}, treats this as not overlapping, so the step
     * is irrelevant to the Y clamp and the box falls freely.
     */
    @Test
    void aFlushButNotOverlappingEdgeDoesNotBlockTheFall() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0.7, 1.1, 0), new Vec3(0, -0.3, 0), stepAtXOneToTwo());

        assertThat(result.allowedMovement().y()).isCloseTo(-0.3, within(1.0e-9));
    }

    /** Y is clamped by the ceiling, but the movement is upward, so onGround must stay false. */
    @Test
    void hittingACeilingWhileRisingIsNotGrounded() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 5, 0), new Vec3(0, 2.0, 0), ceilingAtSeven());

        assertThat(result.allowedMovement().y()).isCloseTo(0.2, within(1.0e-9));
        assertThat(result.onGround()).isFalse();
    }

    /**
     * {@code Direction.axisStepOrder}: {@code |dx| < |dz|} resolves Z before X, not X before Z.
     * With {@code dx = 0.3} and {@code dz = 0.6} against a pillar finite on both x and z, the box
     * starts clear of the pillar on both axes (a diagonal approach, mirroring how the step test
     * above forces Y before X). Resolving Z first finds no x-overlap yet, so Z moves the full 0.6;
     * X is then checked against the Z-shifted box, which now overlaps the pillar's z-range, and
     * clamps to 0.2. Resolving X first (the wrong, fixed Y-X-Z order) would instead find no
     * z-overlap yet, move X the full 0.3, then clamp Z to 0.2 against the X-shifted box — the two
     * orders disagree on both components, exactly as the step test's Y-vs-X pair did.
     */
    @Test
    void whenZMovesFurtherThanXZIsResolvedBeforeX() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0.5, 10, 0.5), new Vec3(0.3, 0, 0.6), pillarAtXOneTwoZOneTwo());

        assertThat(result.allowedMovement().x()).isCloseTo(0.2, within(1.0e-9));
        assertThat(result.allowedMovement().z()).isCloseTo(0.6, within(1.0e-9));
    }

    /**
     * The only test that clamps Z and checks {@code horizontalCollision} off the back of it: a
     * {@code clampZ} that never clamps, or a {@code horizontalCollision} that ignores its Z term,
     * both still pass every other test in this class (only this test and
     * {@link #anAxisWithoutAnObstacleIsUnaffectedByOneThatHasOne} move in Z, and that one expects Z
     * unclamped either way).
     */
    @Test
    void aWallOnZStopsMovementAndReportsHorizontalCollision() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(0, 0, 10.0),
                filtering(new Aabb(new Vec3(-64, -64, 5), new Vec3(64, 64, 6))));

        assertThat(result.allowedMovement().z()).isCloseTo(4.7, within(1.0e-9));
        assertThat(result.horizontalCollision()).isTrue();
    }

    /**
     * Every clamp distance elsewhere in this suite is either exactly {@code 0.0} or at least
     * {@code 0.1} — nothing falls inside {@code (0, 1e-5)}, Vanilla's {@code Mth.equal} tolerance on
     * {@code horizontalCollision}. A resolver whose tolerance check silently regressed to an exact
     * {@code ==} would still pass every other test here. This one requests {@code dx = 4.700005}
     * against the wall at {@code x = 5..6}: the wall clamps it to {@code 4.7} exactly (same
     * derivation as {@link #aWallStopsHorizontalMovementAndReportsIt}: {@code box.max.x() (0.3) <=
     * other.min.x() (5)}, {@code limit = 5 - 0.3 = 4.7}), leaving a requested-vs-achieved difference
     * of exactly {@code 5e-6} — under Vanilla's {@code 1.0E-5F} — so {@code horizontalCollision} must
     * stay false even though the movement was, in fact, clamped.
     */
    @Test
    void aClampWellUnderVanillasToleranceIsNotReportedAsACollision() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(4.700005, 0, 0),
                filtering(new Aabb(new Vec3(5, -64, -64), new Vec3(6, 64, 64))));

        assertThat(result.allowedMovement().x()).isCloseTo(4.7, within(1.0e-9));
        assertThat(result.horizontalCollision()).isFalse();
    }

    /**
     * The same sub-tolerance clamp as {@link #aClampWellUnderVanillasToleranceIsNotReportedAsACollision}
     * — {@code dx = 4.700005} clamped to exactly {@code 4.7}, a {@code 5e-6} difference, under
     * Vanilla's {@code 1.0E-5F} tolerance — but read through {@link MovementResult#xCollision()}
     * instead of {@link MovementResult#horizontalCollision()}. Restitution (zeroing a collided axis'
     * velocity, done by the caller in {@code ElytraSimulator}) must act on the exact clamp, not the
     * tolerance-gated flag: a resolver that computed {@code xCollision} with the same tolerance as
     * {@code horizontalCollision} would still pass every other test in this class, including {@link
     * #aClampWellUnderVanillasToleranceIsNotReportedAsACollision} itself, since that one only checks
     * {@code horizontalCollision}.
     */
    @Test
    void anExactSubToleranceClampStillReportsAxisCollision() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(4.700005, 0, 0),
                filtering(new Aabb(new Vec3(5, -64, -64), new Vec3(6, 64, 64))));

        assertThat(result.xCollision()).isTrue();
        assertThat(result.horizontalCollision()).isFalse();
    }

    @Test
    void verticalCollisionIsTrueOnLanding() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 0.5, 0), new Vec3(0, -2.0, 0), floorAtZero());

        assertThat(result.verticalCollision()).isTrue();
        assertThat(result.onGround()).isTrue();
    }

    /**
     * {@code onGround} is landing-specific ({@code moving down AND clamped}); {@code
     * verticalCollision} is broader — clamped in either direction. Hitting a ceiling while rising
     * clamps Y (so {@code verticalCollision} is true) without landing (so {@code onGround} stays
     * false) — the one case in this suite where the two vertical flags disagree.
     */
    @Test
    void verticalCollisionIsTrueOnACeilingHitEvenThoughOnGroundIsFalse() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 5, 0), new Vec3(0, 2.0, 0), ceilingAtSeven());

        assertThat(result.verticalCollision()).isTrue();
        assertThat(result.onGround()).isFalse();
    }

    @Test
    void zCollisionIsTrueWhenZIsClamped() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(0, 0, 10.0),
                filtering(new Aabb(new Vec3(-64, -64, 5), new Vec3(64, 64, 6))));

        assertThat(result.zCollision()).isTrue();
    }

    @Test
    void anUnobstructedAxisReportsNoExactCollision() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(1, -0.5, 2), CollisionSpace.empty());

        assertThat(result.xCollision()).isFalse();
        assertThat(result.verticalCollision()).isFalse();
        assertThat(result.zCollision()).isFalse();
    }
}
