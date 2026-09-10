package net.elytrarace.voyager.physics;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.physics.step.ElytraStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElytraSimulatorTest {

    private static final double DEFAULT_GRAVITY = 0.08;

    private static FlightState gliding(double y, Vec3 velocity) {
        return new FlightState(new Vec3(0, y, 0), velocity, 0.0f, -5.0f, false);
    }

    private static FlightInput level() {
        return new FlightInput(0.0f, -5.0f, false, 0, DEFAULT_GRAVITY);
    }

    private static CollisionSpace floorAtZero() {
        return region -> List.of(new Aabb(new Vec3(-64, -1, -64), new Vec3(64, 0, 64)));
    }

    /** A wall spanning the entity's altitude range, unbounded on y and z: x in {@code [minX, minX + 1]}. */
    private static CollisionSpace wallAt(double minX) {
        return region -> List.of(new Aabb(new Vec3(minX, -64, -64), new Vec3(minX + 1.0, 64, 64)));
    }

    /** A ceiling, unbounded on x and z: y in {@code [minY, minY + 1]}. */
    private static CollisionSpace ceilingAt(double minY) {
        return region -> List.of(new Aabb(new Vec3(-64, minY, -64), new Vec3(64, minY + 1.0, 64)));
    }

    @Test
    void aLevelGlideLosesAltitudeAndGainsForwardSpeed() {
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));

        for (int tick = 0; tick < 20; tick++) {
            state = ElytraSimulator.tick(state, level(), CollisionSpace.empty());
        }

        assertThat(state.position().y()).isLessThan(100.0);
        assertThat(state.position().z()).isGreaterThan(0.0);
        assertThat(state.velocity().z()).isGreaterThan(0.5);
    }

    @Test
    void isAPureFunctionOfItsArguments() {
        FlightState before = gliding(100.0, new Vec3(0, 0, 0.5));

        FlightState first = ElytraSimulator.tick(before, level(), CollisionSpace.empty());
        FlightState second = ElytraSimulator.tick(before, level(), CollisionSpace.empty());

        assertThat(first).isEqualTo(second);
        assertThat(before.position()).isEqualTo(new Vec3(0, 100.0, 0));
        assertThat(before.velocity()).isEqualTo(new Vec3(0, 0, 0.5));
    }

    @Test
    void theTracedFormReportsOneVelocityPerStepInOrder() {
        TickTrace trace = ElytraSimulator.tickTraced(
                gliding(100.0, new Vec3(0, 0, 0.5)), level(), CollisionSpace.empty());

        assertThat(trace.velocityAfter()).hasSize(ElytraStep.values().length);
        assertThat(trace.velocityAfter().keySet()).containsExactly(ElytraStep.values());
    }

    @Test
    void theStepContextIsBuiltOnceFromTheVelocityEnteringTheTick() {
        // Pinned by hand-driving StepContext.of + ElytraStep in build-once order, independently of
        // ElytraSimulator, for entering velocity (0, 0, 0.5), pitch -5, yaw 0, gravity 0.08.
        // Rebuilding StepContext from the in-progress velocity before each step (using the current,
        // partially-updated velocity's horizontal length instead of the entering one) changes
        // moveHorLength starting at UPWARD_PITCH_BOOST and diverges by ~2e-5 on z by the end of the
        // tick — three orders of magnitude past this tolerance.
        TickTrace trace = ElytraSimulator.tickTraced(
                gliding(100.0, new Vec3(0, 0, 0.5)), level(), CollisionSpace.empty());

        Vec3 afterUpwardPitchBoost = trace.velocityAfter().get(ElytraStep.UPWARD_PITCH_BOOST);
        assertThat(afterUpwardPitchBoost.z()).isCloseTo(0.500287347931114, within(1.0e-12));

        Vec3 finalVelocity = trace.velocityAfter().get(ElytraStep.DRAG);
        assertThat(finalVelocity.y()).isCloseTo(-0.012592137808631509, within(1.0e-12));
        assertThat(finalVelocity.z()).isCloseTo(0.49525603177746047, within(1.0e-12));
    }

    @Test
    void theTracedFormAgreesWithThePlainForm() {
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));

        FlightState plain = ElytraSimulator.tick(state, level(), CollisionSpace.empty());
        TickTrace traced = ElytraSimulator.tickTraced(state, level(), CollisionSpace.empty());

        assertThat(traced.result()).isEqualTo(plain);
    }

    @Test
    void aFireworkBoostAcceleratesTowardsTheLookDirection() {
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));
        FlightInput boosting = new FlightInput(0.0f, -5.0f, true, 1, DEFAULT_GRAVITY);

        FlightState boosted = ElytraSimulator.tick(state, boosting, CollisionSpace.empty());
        FlightState coasting = ElytraSimulator.tick(state, level(), CollisionSpace.empty());

        assertThat(boosted.velocity().z()).isGreaterThan(coasting.velocity().z());
    }

    @Test
    void theFireworkBoostIsAppliedBeforeTheSteps() {
        // Pinned by hand-driving the boost formula and ElytraStep in boost-then-steps order,
        // independently of ElytraSimulator, for entering velocity (0, 0, 0.5), pitch -5, yaw 0,
        // gravity 0.08. Applying the boost after the steps instead feeds the unboosted velocity into
        // every step's moveHorLength/liftForce terms and diverges by ~0.012 on z by the end of the
        // tick — nine orders of magnitude past this tolerance.
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));
        FlightInput boosting = new FlightInput(0.0f, -5.0f, true, 1, DEFAULT_GRAVITY);

        FlightState boosted = ElytraSimulator.tick(state, boosting, CollisionSpace.empty());

        assertThat(boosted.velocity().y()).isCloseTo(0.06452415797364021, within(1.0e-12));
        assertThat(boosted.velocity().z()).isCloseTo(1.0823864405206898, within(1.0e-12));
    }

    @Test
    void aGlideIntoTheGroundStopsAndReportsBeingGrounded() {
        FlightState state = gliding(0.2, new Vec3(0, -1.0, 0.5));

        FlightState landed = ElytraSimulator.tick(state, level(), floorAtZero());

        assertThat(landed.onGround()).isTrue();
        assertThat(landed.position().y()).isEqualTo(0.0);
    }

    @Test
    void theEntityNeverSinksBelowTheFloorAcrossManyTicks() {
        FlightState state = gliding(3.0, new Vec3(0, -0.5, 0.3));

        for (int tick = 0; tick < 60; tick++) {
            state = ElytraSimulator.tick(state, level(), floorAtZero());
            assertThat(state.position().y())
                    .as("tick %s".formatted(tick))
                    .isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    void theEntityWidthIsPinnedByAWallAtAKnownDistance() {
        // Entering velocity (50, 0, 0), pitch 0, yaw 0, gravity 0.08 — verified end-to-end against
        // ElytraSimulator.tick itself (not hand-derived) to reach a wall at x = [10, 11] with room
        // to spare. The box's half-width (0.3, a 0.6-block-wide entity) is the only thing standing
        // between the box and the wall, so the clamped position pins it: 10 - 0.3 = 9.7. A
        // half-width of 0.05 would instead clamp to 9.95.
        FlightState state = new FlightState(new Vec3(0, 10, 0), new Vec3(50, 0, 0), 0.0f, 0.0f, false);
        FlightInput input = new FlightInput(0.0f, 0.0f, false, 0, DEFAULT_GRAVITY);

        FlightState result = ElytraSimulator.tick(state, input, wallAt(10.0));

        assertThat(result.position().x()).isCloseTo(9.7, within(1.0e-9));
    }

    @Test
    void theEntityHeightIsPinnedByACeilingAtAKnownDistance() {
        // Entering velocity (0, 10, 0), pitch 0, yaw 0, gravity 0.08 — verified end-to-end against
        // ElytraSimulator.tick itself to reach a ceiling at y = [102, 103] with room to spare. The
        // box's height (1.8) is the only thing standing between the box's top and the ceiling, so
        // the clamped position pins it: 102 - 1.8 = 100.2. A height of 0.5 would instead clamp to
        // 101.5.
        FlightState state = new FlightState(new Vec3(0, 100, 0), new Vec3(0, 10, 0), 0.0f, 0.0f, false);
        FlightInput input = new FlightInput(0.0f, 0.0f, false, 0, DEFAULT_GRAVITY);

        FlightState result = ElytraSimulator.tick(state, input, ceilingAt(102.0));

        assertThat(result.position().y()).isCloseTo(100.2, within(1.0e-9));
    }

    @Test
    void theResultingRotationComesFromTheInputNotThePreviousState() {
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5)); // yaw 0, pitch -5
        FlightInput input = new FlightInput(37.0f, 12.0f, false, 0, DEFAULT_GRAVITY);

        FlightState result = ElytraSimulator.tick(state, input, CollisionSpace.empty());

        assertThat(result.yaw()).isEqualTo(37.0f);
        assertThat(result.pitch()).isEqualTo(12.0f);
    }

    @Test
    void gravityIsThreadedFromTheInputNotHardcoded() {
        // Same entering velocity/rotation as theStepContextIsBuiltOnceFromTheVelocityEnteringTheTick,
        // but gravity 0.01 (Slow Falling's clamp) instead of 0.08 — hand-computed via StepContext +
        // ElytraStep, independently of ElytraSimulator. A simulator that silently used a hardcoded
        // 0.08 would instead produce y = -0.012592137808631509 (this same entering state's pinned
        // 0.08 value from theStepContextIsBuiltOnceFromTheVelocityEnteringTheTick) and diverge by an
        // order of magnitude, including a sign flip on y.
        FlightState state = gliding(100.0, new Vec3(0, 0, 0.5));
        FlightInput input = new FlightInput(0.0f, -5.0f, false, 0, 0.01);

        FlightState result = ElytraSimulator.tick(state, input, CollisionSpace.empty());

        assertThat(result.velocity().y()).isCloseTo(0.0032079251999737844, within(1.0e-12));
        assertThat(result.velocity().z()).isCloseTo(0.4936733631637259, within(1.0e-12));
    }

    @Test
    void aCollisionZeroesTheCollidedVelocityComponent() {
        // Vanilla's restitution at bounciness = 0.0 (26.2's default for every ordinary block): a
        // collided axis' velocity becomes exactly 0.0, not the pre-collision value carried through
        // unchanged. Reuses aGlideIntoTheGroundStopsAndReportsBeingGrounded's own fixture.
        FlightState state = gliding(0.2, new Vec3(0, -1.0, 0.5));

        FlightState landed = ElytraSimulator.tick(state, level(), floorAtZero());

        assertThat(landed.velocity().y()).isEqualTo(0.0);
    }

    @Test
    void restitutionZeroesOnTheExactClampNotTheTolerantOne() {
        // Same entering velocity/rotation as theEntityWidthIsPinnedByAWallAtAKnownDistance
        // (unclamped velocity.x verified end-to-end at 44.55000042915344), but the wall is placed so
        // the clamp differs from the requested movement by exactly 5e-6 — under Vanilla's 1.0E-5F
        // horizontalCollision tolerance, so MovementResult.horizontalCollision() is false, while
        // MovementResult.xCollision() (no tolerance) is true. Restitution must zero velocity.x on the
        // exact flag: a simulator that zeroed on horizontalCollision instead would leave velocity.x
        // at ~44.55.
        FlightState state = new FlightState(new Vec3(0, 10, 0), new Vec3(50, 0, 0), 0.0f, 0.0f, false);
        FlightInput input = new FlightInput(0.0f, 0.0f, false, 0, DEFAULT_GRAVITY);

        FlightState result = ElytraSimulator.tick(state, input, wallAt(44.84999542915344));

        assertThat(result.velocity().x()).isEqualTo(0.0);
    }
}
