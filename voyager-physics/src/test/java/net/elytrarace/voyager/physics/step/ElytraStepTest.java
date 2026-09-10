package net.elytrarace.voyager.physics.step;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.math.MinecraftMath;
import net.elytrarace.voyager.physics.math.ViewVector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElytraStepTest {

    private static final double DEFAULT_GRAVITY = 0.08;

    private static StepContext context(Vec3 velocity, float pitch, float yaw) {
        return StepContext.of(velocity, pitch, yaw, DEFAULT_GRAVITY);
    }

    @Test
    void theStepsRunInVanillaOrder() {
        assertThat(ElytraStep.values()).containsExactly(
                ElytraStep.GRAVITY_AND_LIFT,
                ElytraStep.DOWNWARD_GLIDE,
                ElytraStep.UPWARD_PITCH_BOOST,
                ElytraStep.DIRECTION_ALIGNMENT,
                ElytraStep.DRAG);
    }

    @Test
    void levelFlightLosesTheDocumentedAmountOfAltitudePerTick() {
        // pitch 0 -> liftForce = cos²(0) = 1 -> velY += 0.08 * (-1 + 0.75) = -0.02
        Vec3 result = ElytraStep.GRAVITY_AND_LIFT.step().apply(Vec3.ZERO, context(Vec3.ZERO, 0.0f, 0.0f));

        assertThat(result.y()).isCloseTo(-0.02, within(1.0e-12));
    }

    @Test
    void straightDownLosesFullGravityBecauseLiftVanishes() {
        // pitch 90 -> cos(90°) ~ 0 -> velY += gravity * -1
        Vec3 result = ElytraStep.GRAVITY_AND_LIFT.step().apply(Vec3.ZERO, context(Vec3.ZERO, 90.0f, 0.0f));

        assertThat(result.y()).isCloseTo(-DEFAULT_GRAVITY, within(1.0e-4));
    }

    @Test
    void gravityScalesBothHalvesOfTheLiftTerm() {
        // The term is gravity * (-1 + liftForce * 0.75), not a constant -0.08 plus a constant lift.
        StepContext halfGravity = StepContext.of(Vec3.ZERO, 0.0f, 0.0f, 0.04);

        Vec3 result = ElytraStep.GRAVITY_AND_LIFT.step().apply(Vec3.ZERO, halfGravity);

        assertThat(result.y()).isCloseTo(-0.01, within(1.0e-12));
    }

    @Test
    void downwardGlideConvertsSinkIntoForwardMotion() {
        Vec3 sinking = new Vec3(0.0, -0.5, 0.0);

        Vec3 result = ElytraStep.DOWNWARD_GLIDE.step().apply(sinking, context(sinking, 0.0f, 0.0f));

        assertThat(result.y()).isGreaterThan(sinking.y());
        assertThat(result.z()).isGreaterThan(0.0);
    }

    @Test
    void downwardGlideIsScaledByLiftForce() {
        // At pitch 60 the lift factor is cos²(60°) = 0.25, so the conversion is a quarter as strong
        // as at pitch 0. A port that dropped the liftForce factor produces the same value for both.
        Vec3 sinking = new Vec3(0.0, -0.5, 0.0);

        double atLevel = ElytraStep.DOWNWARD_GLIDE.step().apply(sinking, context(sinking, 0.0f, 0.0f)).y();
        double atSteep = ElytraStep.DOWNWARD_GLIDE.step().apply(sinking, context(sinking, 60.0f, 0.0f)).y();

        assertThat(atSteep - sinking.y()).isLessThan(atLevel - sinking.y());
    }

    @Test
    void downwardGlideDoesNothingWhileRising() {
        Vec3 rising = new Vec3(0.0, 0.5, 0.0);

        assertThat(ElytraStep.DOWNWARD_GLIDE.step().apply(rising, context(rising, 0.0f, 0.0f)))
                .isEqualTo(rising);
    }

    @Test
    void upwardPitchBoostTradesHorizontalSpeedForClimb() {
        Vec3 fast = new Vec3(0.0, 0.0, 1.0);
        StepContext climbing = context(fast, -30.0f, 0.0f);

        Vec3 result = ElytraStep.UPWARD_PITCH_BOOST.step().apply(fast, climbing);

        assertThat(result.y()).isGreaterThan(0.0);
        assertThat(result.z()).isLessThan(fast.z());
    }

    @Test
    void upwardPitchBoostDoesNothingWhilePitchedDown() {
        Vec3 fast = new Vec3(0.0, 0.0, 1.0);

        assertThat(ElytraStep.UPWARD_PITCH_BOOST.step().apply(fast, context(fast, 10.0f, 0.0f)))
                .isEqualTo(fast);
    }

    @Test
    void upwardPitchBoostLiftsByThreePointTwoTimesTheTradedSpeed() {
        Vec3 fast = new Vec3(0.0, 0.0, 1.0);
        StepContext climbing = context(fast, -30.0f, 0.0f);
        float leanAngle = -30.0f * MinecraftMath.DEG_TO_RAD;
        double convert = 1.0 * -MinecraftMath.sin(leanAngle) * 0.04;

        Vec3 result = ElytraStep.UPWARD_PITCH_BOOST.step().apply(fast, climbing);

        assertThat(result.y()).isCloseTo(convert * 3.2, within(1.0e-12));
    }

    @Test
    void directionAlignmentPullsVelocityTowardsTheLookDirection() {
        Vec3 sideways = new Vec3(1.0, 0.0, 0.0);
        StepContext lookingForward = context(sideways, 0.0f, 0.0f);

        Vec3 result = ElytraStep.DIRECTION_ALIGNMENT.step().apply(sideways, lookingForward);

        assertThat(result.x()).isLessThan(sideways.x());
        assertThat(result.z()).isGreaterThan(sideways.z());
        assertThat(result.y()).isEqualTo(sideways.y());
    }

    @Test
    void dragUsesTheWidenedFloatConstantsNotTheDoubleLiterals() {
        // Trap 1. 0.99 and 0.99F are different numbers once widened, and the difference compounds
        // every tick in the one operation that touches every component.
        Vec3 velocity = new Vec3(1.0, 1.0, 1.0);

        Vec3 result = ElytraStep.DRAG.step().apply(velocity, context(velocity, 0.0f, 0.0f));

        assertThat(result.x()).isEqualTo((double) 0.99f);
        assertThat(result.y()).isEqualTo((double) 0.98f);
        assertThat(result.z()).isEqualTo((double) 0.99f);
        assertThat(result.x()).isNotEqualTo(0.99);
        assertThat(result.y()).isNotEqualTo(0.98);
    }

    @Test
    void theLeanAngleIsComputedInFloatNotViaMathToRadians() {
        // Trap 2.
        float pitch = -12.5f;
        StepContext ctx = context(Vec3.ZERO, pitch, 0.0f);

        assertThat(ctx.leanAngle()).isEqualTo(pitch * MinecraftMath.DEG_TO_RAD);
        assertThat((double) ctx.leanAngle()).isNotEqualTo(Math.toRadians(pitch));
    }

    @Test
    void theContextExposesTheLookVectorForTheGivenRotation() {
        StepContext ctx = context(Vec3.ZERO, 20.0f, 45.0f);

        assertThat(ctx.lookAngle()).isEqualTo(ViewVector.of(20.0f, 45.0f));
    }
}
