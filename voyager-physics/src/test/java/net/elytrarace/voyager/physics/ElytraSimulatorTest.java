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
}
