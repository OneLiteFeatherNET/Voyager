package net.elytrarace.voyager.physics;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.physics.math.MinecraftMath;
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

    /** A wall spanning the entity's altitude range, unbounded on y and x: z in {@code [minZ, minZ + 1]}. */
    private static CollisionSpace wallAtZ(double minZ) {
        return region -> List.of(new Aabb(new Vec3(-64, -64, minZ), new Vec3(64, 64, minZ + 1.0)));
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

    /**
     * No simulator-level fixture ever produced a Z collision — every collision fixture above clamps
     * X or Y — so deleting {@code movementResult.zCollision() ? 0.0 :} from {@code restitute} left
     * the whole suite green while an entity flying into a wall on Z kept its full Z velocity. The
     * {@code +X} mirror of this is {@code restitutionZeroesOnTheExactClampNotTheTolerantOne}.
     */
    @Test
    void aZCollisionZeroesTheZVelocityAndClampsTheZPosition() {
        FlightState state = new FlightState(new Vec3(0, 10, 0), new Vec3(0, 0, 50), 0.0f, 0.0f, false);
        FlightInput input = new FlightInput(0.0f, 0.0f, false, 0, DEFAULT_GRAVITY);

        FlightState result = ElytraSimulator.tick(state, input, wallAtZ(10.0));

        // Same derivation as theEntityWidthIsPinnedByAWallAtAKnownDistance, on Z: 10 - 0.3 = 9.7.
        assertThat(result.position().z()).isCloseTo(9.7, within(1.0e-9));
        assertThat(result.velocity().z()).isEqualTo(0.0);
        // Y is untouched by the wall, so its velocity must survive restitution unchanged.
        assertThat(result.velocity().y()).isLessThan(0.0);
    }

    // --- Fix-Runde 2: the X terms, which every fixture above collapses to zero at yaw 0. ---

    /**
     * At yaw 0 {@code MinecraftMath.sin(-0.0f)} returns exactly {@code 0.0f}, so {@code lookAngle.x}
     * is exactly zero and every X contribution in the pipeline vanishes: the {@code lookAngle.x}
     * terms of {@code DOWNWARD_GLIDE} and {@code UPWARD_PITCH_BOOST}, and the {@code lookAngle.x}
     * half of the firework impulse, are all multiplied by zero. Every other numeric fixture in this
     * class, in {@code ElytraStepTest} and in {@code TraceReplayTest} runs at yaw 0, so flipping the
     * sign of either of those two step terms — or changing the impulse's {@code 1.5} to {@code 1.4}
     * — leaves all of them green.
     *
     * <p>This fixture runs at pitch {@code -20}, yaw {@code 37}: none of {@code sin(realYRot)},
     * {@code cos(realYRot)}, {@code sin(realXRot)} or {@code cos(realXRot)} is {@code 0} or
     * {@code ±1} there, so no term degenerates. The entering velocity has a horizontal speed of
     * exactly {@code 1.0} and a negative {@code y}, so {@code DOWNWARD_GLIDE}'s guard
     * ({@code movement.y < 0}) and {@code UPWARD_PITCH_BOOST}'s ({@code leanAngle < 0}) both fire.
     *
     * <p>Expected values come from {@link #vanillaUpdateFallFlyingMovement}, a transcription of the
     * source quoted in {@code docs/reference/elytra-physics-26.2.md} written in plain {@code double}
     * arithmetic and reading nothing from the production pipeline. The literals below were produced
     * by that transcription, not by running {@link ElytraSimulator} and recording its output; they
     * are asserted alongside it so that editing the transcription cannot silently move the target.
     * Measured against this fixture, flipping {@code DOWNWARD_GLIDE}'s {@code lookAngle.x} sign
     * moves the resulting {@code x} by {@code 1.20e-2}, flipping {@code UPWARD_PITCH_BOOST}'s by
     * {@code 1.47e-2}.
     */
    @Test
    void theXTermsOfEveryStepArePinnedAtANonZeroYaw() {
        FlightState state = new FlightState(new Vec3(0, 100, 0), ENTERING_VELOCITY, YAW, PITCH, false);
        FlightInput input = new FlightInput(YAW, PITCH, false, 0, DEFAULT_GRAVITY);

        FlightState result = ElytraSimulator.tick(state, input, CollisionSpace.empty());

        Vec3 expected = vanillaUpdateFallFlyingMovement(ENTERING_VELOCITY, PITCH, YAW, DEFAULT_GRAVITY);
        assertThat(expected.x()).isCloseTo(0.47634234574166134, within(1.0e-15));
        assertThat(expected.y()).isCloseTo(-0.0705936213721782, within(1.0e-15));
        assertThat(expected.z()).isCloseTo(0.7901148825981188, within(1.0e-15));

        assertThat(result.velocity().x()).isCloseTo(expected.x(), within(1.0e-12));
        assertThat(result.velocity().y()).isCloseTo(expected.y(), within(1.0e-12));
        assertThat(result.velocity().z()).isCloseTo(expected.z(), within(1.0e-12));
        assertThat(result.position().x()).isCloseTo(expected.x(), within(1.0e-12));
        assertThat(result.position().z()).isCloseTo(expected.z(), within(1.0e-12));
    }

    /**
     * The same rotation as {@link #theXTermsOfEveryStepArePinnedAtANonZeroYaw}, with the firework
     * impulse active. The impulse's {@code lookAngle.x * 1.5} term is the one every yaw-0 fixture
     * multiplies by zero: at this yaw, changing that {@code 1.5} to {@code 1.4} moves the entering
     * {@code x} by {@code 2.83e-2} and the resulting {@code x} by {@code 2.54e-2}.
     */
    @Test
    void theFireworkImpulsesXTermIsPinnedAtANonZeroYaw() {
        FlightState state = new FlightState(new Vec3(0, 100, 0), ENTERING_VELOCITY, YAW, PITCH, false);
        FlightInput boosting = new FlightInput(YAW, PITCH, true, 1, DEFAULT_GRAVITY);

        FlightState result = ElytraSimulator.tick(state, boosting, CollisionSpace.empty());

        Vec3 boosted = vanillaFireworkImpulse(ENTERING_VELOCITY, vanillaLookAngle(PITCH, YAW));
        assertThat(boosted.x()).isCloseTo(-0.18065171241760258, within(1.0e-15));

        Vec3 expected = vanillaUpdateFallFlyingMovement(boosted, PITCH, YAW, DEFAULT_GRAVITY);
        assertThat(expected.x()).isCloseTo(-0.21599867432561282, within(1.0e-15));
        assertThat(expected.y()).isCloseTo(0.25454496876292027, within(1.0e-15));
        assertThat(expected.z()).isCloseTo(0.9977951010981996, within(1.0e-15));

        assertThat(result.velocity().x()).isCloseTo(expected.x(), within(1.0e-12));
        assertThat(result.velocity().y()).isCloseTo(expected.y(), within(1.0e-12));
        assertThat(result.velocity().z()).isCloseTo(expected.z(), within(1.0e-12));
    }

    // --- An independent transcription of Vanilla 26.2, used only by the two fixtures above. ---
    //
    // Transcribed from the source quoted in docs/reference/elytra-physics-26.2.md
    // (LivingEntity.updateFallFlyingMovement, Entity.calculateViewVector, FireworkRocketEntity.tick)
    // in plain double arithmetic. It calls nothing in net.elytrarace.voyager.physics except
    // MinecraftMath, whose table and DEG_TO_RAD are pinned independently by MinecraftMathTest — so a
    // mutation anywhere in ElytraStep, StepContext, ViewVector or ElytraSimulator cannot move both
    // sides of the comparison together.

    private static final float PITCH = -20.0f;
    private static final float YAW = 37.0f;
    private static final Vec3 ENTERING_VELOCITY = new Vec3(0.6, -0.1, 0.8);

    /** {@code Entity.calculateViewVector(xRot, yRot)}. */
    private static Vec3 vanillaLookAngle(float xRot, float yRot) {
        float realXRot = xRot * MinecraftMath.DEG_TO_RAD;
        float realYRot = -yRot * MinecraftMath.DEG_TO_RAD;
        float yCos = MinecraftMath.cos(realYRot);
        float ySin = MinecraftMath.sin(realYRot);
        float xCos = MinecraftMath.cos(realXRot);
        float xSin = MinecraftMath.sin(realXRot);
        return new Vec3(ySin * xCos, -xSin, yCos * xCos);
    }

    /** {@code FireworkRocketEntity.tick()}'s impulse on the entity it is attached to. */
    private static Vec3 vanillaFireworkImpulse(Vec3 movement, Vec3 lookAngle) {
        return new Vec3(
                movement.x() + (lookAngle.x() * 0.1 + (lookAngle.x() * 1.5 - movement.x()) * 0.5),
                movement.y() + (lookAngle.y() * 0.1 + (lookAngle.y() * 1.5 - movement.y()) * 0.5),
                movement.z() + (lookAngle.z() * 0.1 + (lookAngle.z() * 1.5 - movement.z()) * 0.5));
    }

    /** {@code LivingEntity.updateFallFlyingMovement(movement)}. */
    private static Vec3 vanillaUpdateFallFlyingMovement(Vec3 movement, float pitch, float yaw, double gravity) {
        Vec3 lookAngle = vanillaLookAngle(pitch, yaw);
        float leanAngle = pitch * MinecraftMath.DEG_TO_RAD;
        double lookHorLength = Math.sqrt(lookAngle.x() * lookAngle.x() + lookAngle.z() * lookAngle.z());
        double moveHorLength = Math.sqrt(movement.x() * movement.x() + movement.z() * movement.z());
        double liftForce = Math.cos(leanAngle) * Math.cos(leanAngle);

        double x = movement.x();
        double y = movement.y() + gravity * (-1.0 + liftForce * 0.75);
        double z = movement.z();

        if (y < 0.0 && lookHorLength > 0.0) {
            double convert = y * -0.1 * liftForce;
            x += lookAngle.x() * convert / lookHorLength;
            z += lookAngle.z() * convert / lookHorLength;
            y += convert;
        }
        if (leanAngle < 0.0F && lookHorLength > 0.0) {
            double convert = moveHorLength * -MinecraftMath.sin(leanAngle) * 0.04;
            x += -lookAngle.x() * convert / lookHorLength;
            z += -lookAngle.z() * convert / lookHorLength;
            y += convert * 3.2;
        }
        if (lookHorLength > 0.0) {
            x += (lookAngle.x() / lookHorLength * moveHorLength - x) * 0.1;
            z += (lookAngle.z() / lookHorLength * moveHorLength - z) * 0.1;
        }
        return new Vec3(x * 0.99F, y * 0.98F, z * 0.99F);
    }
}
