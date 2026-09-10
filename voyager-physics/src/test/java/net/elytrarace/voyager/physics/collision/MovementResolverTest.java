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

    /**
     * Candidates in exactly the given order, regardless of the queried region. Vanilla's snap guard
     * is positional — it fires on the candidate <em>after</em> the one that shrank the distance —
     * so iteration order is part of any fixture that exercises it, and {@link #filtering} sorts
     * nothing.
     */
    private static CollisionSpace inOrder(Aabb... blocks) {
        List<Aabb> all = List.of(blocks);
        return region -> all;
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
     * Vanilla's {@code 1.0E-5F} tolerance — read through {@link MovementResult#xCollision()}, the
     * per-axis flag rather than the derived one. Vanilla has no second, exact per-axis pair:
     * {@code Entity.move:760-762} computes both horizontal flags through {@code Mth.equal} and
     * {@code :786} hands those same tolerant flags to {@code restituteMovementAfterCollisions}. So
     * the movement is clamped here and yet neither flag reports a collision — and the caller's
     * restitution consequently leaves the velocity alone, which
     * {@code ElytraSimulatorTest.restitutionPreservesVelocityOnASubToleranceHorizontalClamp} pins
     * end to end. A resolver that computed {@code xCollision} exactly ({@code clamped != requested},
     * as this port did before the correction) fails this test on its {@code xCollision} assertion,
     * and — because {@link MovementResult#horizontalCollision()} is now derived from the per-axis
     * flags rather than computed separately — fails
     * {@link #aClampWellUnderVanillasToleranceIsNotReportedAsACollision} with it.
     *
     * <p>The {@code allowedMovement} assertion is load-bearing: without it a resolver that never
     * clamped at all would satisfy the two flag assertions trivially.
     */
    @Test
    void aSubToleranceClampReportsNoAxisCollisionEither() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(4.700005, 0, 0),
                filtering(new Aabb(new Vec3(5, -64, -64), new Vec3(6, 64, 64))));

        assertThat(result.allowedMovement().x()).isCloseTo(4.7, within(1.0e-9));
        assertThat(result.xCollision()).isFalse();
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
    void anUnobstructedAxisReportsNoAxisCollision() {
        MovementResult result =
                MovementResolver.resolve(boxAt(0, 10, 0), new Vec3(1, -0.5, 2), CollisionSpace.empty());

        assertThat(result.xCollision()).isFalse();
        assertThat(result.verticalCollision()).isFalse();
        assertThat(result.zCollision()).isFalse();
    }

    // --- Fix-Runde 2: the negative direction, and exact-flush contact. ---

    /**
     * Every horizontal fixture above moves in {@code +X} or {@code +Z}, so {@code clampX}'s and
     * {@code clampZ}'s {@code else if (result < 0.0 ...)} branches were never entered: deleting them
     * outright left the whole suite green while an entity flying in {@code -X} passed straight
     * through a wall. Mirror of {@link #aWallStopsHorizontalMovementAndReportsIt}, including its use
     * of {@link #filtering} so the swept region stays load-bearing.
     */
    @Test
    void aWallInNegativeXStopsHorizontalMovementAndReportsIt() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(-10.0, 0, 0),
                filtering(new Aabb(new Vec3(-6, -64, -64), new Vec3(-5, 64, 64))));

        // box.min.x (-0.3) >= other.max.x (-5), so limit = -5 - (-0.3) = -4.7.
        assertThat(result.allowedMovement().x()).isCloseTo(-4.7, within(1.0e-9));
        assertThat(result.xCollision()).isTrue();
        assertThat(result.horizontalCollision()).isTrue();
    }

    /** The {@code -Z} mirror of {@link #aWallInNegativeXStopsHorizontalMovementAndReportsIt}. */
    @Test
    void aWallInNegativeZStopsHorizontalMovementAndReportsIt() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(0, 0, -10.0),
                filtering(new Aabb(new Vec3(-64, -64, -6), new Vec3(64, 64, -5))));

        assertThat(result.allowedMovement().z()).isCloseTo(-4.7, within(1.0e-9));
        assertThat(result.zCollision()).isTrue();
        assertThat(result.horizontalCollision()).isTrue();
    }

    // Exact-flush contact — a face of the box sitting exactly on a face of the block — is not an
    // exotic state: it is the state a box is in on every tick after a collision clamped that axis to
    // the surface and restitution zeroed the velocity. Each clamp's comparison is therefore
    // non-strict (`<=` / `>=`), and relaxing it to `<` / `>` lets the box move through the surface it
    // is already touching. Only aBoxAlreadyRestingOnTheFloorCannotSinkIntoIt covered any of the six;
    // the five fixtures below cover the rest.

    /** {@code clampY}, upward: {@code box.max.y() <= other.min.y()}. */
    @Test
    void aBoxFlushUnderACeilingCannotRiseIntoIt() {
        // boxAt(0, 5.2, 0).max.y == 7.0, exactly the ceiling's min.y.
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 5.2, 0), new Vec3(0, 2.0, 0), ceilingAtSeven());

        assertThat(result.allowedMovement().y()).isEqualTo(0.0);
        assertThat(result.verticalCollision()).isTrue();
        assertThat(result.onGround()).isFalse();
    }

    /** {@code clampX}, positive: {@code box.max.x() <= other.min.x()}. */
    @Test
    void aBoxFlushAgainstAWallInPositiveXCannotAdvanceIntoIt() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(1.0, 0, 0),
                filtering(new Aabb(new Vec3(0.3, -64, -64), new Vec3(1.3, 64, 64))));

        assertThat(result.allowedMovement().x()).isEqualTo(0.0);
        assertThat(result.xCollision()).isTrue();
    }

    /** {@code clampX}, negative: {@code box.min.x() >= other.max.x()}. */
    @Test
    void aBoxFlushAgainstAWallInNegativeXCannotAdvanceIntoIt() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(-1.0, 0, 0),
                filtering(new Aabb(new Vec3(-1.3, -64, -64), new Vec3(-0.3, 64, 64))));

        assertThat(result.allowedMovement().x()).isEqualTo(0.0);
        assertThat(result.xCollision()).isTrue();
    }

    /** {@code clampZ}, positive: {@code box.max.z() <= other.min.z()}. */
    @Test
    void aBoxFlushAgainstAWallInPositiveZCannotAdvanceIntoIt() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(0, 0, 1.0),
                filtering(new Aabb(new Vec3(-64, -64, 0.3), new Vec3(64, 64, 1.3))));

        assertThat(result.allowedMovement().z()).isEqualTo(0.0);
        assertThat(result.zCollision()).isTrue();
    }

    /** {@code clampZ}, negative: {@code box.min.z() >= other.max.z()}. */
    @Test
    void aBoxFlushAgainstAWallInNegativeZCannotAdvanceIntoIt() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(0, 0, -1.0),
                filtering(new Aabb(new Vec3(-64, -64, -1.3), new Vec3(64, 64, -0.3))));

        assertThat(result.allowedMovement().z()).isEqualTo(0.0);
        assertThat(result.zCollision()).isTrue();
    }

    // --- Fix-Runde 3: Shapes.collide's sub-1.0E-7 snap. ---

    /**
     * {@code Shapes.collide}'s {@code Math.abs(distance) < 1.0E-7} guard sits <em>inside</em> the
     * loop over shapes, so an empty list never reaches it and the distance comes back untouched. It
     * is not a blanket "snap small movements to zero": a resolver that checked the guard before the
     * loop — the obvious misreading — would return {@code 0.0} here and freeze an entity drifting
     * through open air at sub-{@code 1.0E-7} speed.
     *
     * <p>{@code 5e-8} is under the guard's threshold and over zero, so {@code resolve}'s
     * {@code lengthSquared() == 0.0} short-circuit does not swallow it either.
     */
    @Test
    void aSubEpsilonMovementWithNoCandidatesPassesThroughUnchanged() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(5.0e-8, 0, 0), CollisionSpace.empty());

        assertThat(result.allowedMovement().x()).isEqualTo(5.0e-8);
        assertThat(result.xCollision()).isFalse();
        assertThat(result.horizontalCollision()).isFalse();
    }

    /**
     * The other half of the guard's position: once an earlier candidate has clamped the remaining
     * distance under {@code 1.0E-7}, the next candidate turns it into exactly {@code 0.0} rather
     * than leaving the residual.
     *
     * <p>The wall's near face sits {@code 5e-8} beyond the box's own ({@code 0.3 + 5e-8}), so it
     * clamps {@code dx = 1.0} to {@code 5e-8} — over zero, under the threshold. The floor is the
     * second candidate and does not overlap the box on Y at all ({@code y in [9, 10]} against a box
     * whose feet are at {@code 10}), which is the point: Vanilla's guard runs before
     * {@code shape.collide}, and the perpendicular-axis test lives <em>inside</em>
     * {@code shape.collide}, so a candidate this clamp would otherwise skip still triggers the snap.
     * A guard placed after the overlap test instead would leave {@code 5e-8} here.
     *
     * <p>{@code xCollision} is true because the flag compares the requested {@code 1.0} against the
     * final {@code 0.0} — {@code Entity.move} never sees the {@code 5e-8} intermediate.
     */
    @Test
    void aClampLeavingLessThanTheSnapEpsilonReturnsExactlyZero() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(1.0, 0, 0),
                inOrder(
                        new Aabb(new Vec3(0.3 + 5.0e-8, -64, -64), new Vec3(1.3, 64, 64)),
                        new Aabb(new Vec3(-64, 9, -64), new Vec3(64, 10, 64))));

        assertThat(result.allowedMovement().x()).isEqualTo(0.0);
        assertThat(result.xCollision()).isTrue();
    }

    /**
     * The same snap, with a requested movement small enough that zeroing it stays inside
     * {@code Mth.equal}'s window. {@code Entity.move:760} compares the requested movement against
     * the final one and nothing in between, so the {@code 5e-6} requested here against a final
     * {@code 0.0} is {@code Mth.equal} — no horizontal collision is reported, even though a wall
     * stopped the movement dead. The vertical flag would report it, being exact; the horizontal
     * pair does not. That asymmetry is Vanilla's, not this port's.
     *
     * <p>Same two candidates as {@link #aClampLeavingLessThanTheSnapEpsilonReturnsExactlyZero}, and
     * the wall is still inside the swept region: {@code 0.3 + 5e-8} is well short of the region's
     * {@code 0.3 + 5e-6 + SWEEP_EPSILON} edge.
     */
    @Test
    void aSnapToZeroBelowTheHorizontalToleranceReportsNoCollision() {
        MovementResult result = MovementResolver.resolve(
                boxAt(0, 10, 0), new Vec3(5.0e-6, 0, 0),
                inOrder(
                        new Aabb(new Vec3(0.3 + 5.0e-8, -64, -64), new Vec3(1.3, 64, 64)),
                        new Aabb(new Vec3(-64, 9, -64), new Vec3(64, 10, 64))));

        assertThat(result.allowedMovement().x()).isEqualTo(0.0);
        assertThat(result.xCollision()).isFalse();
        assertThat(result.horizontalCollision()).isFalse();
    }
}
