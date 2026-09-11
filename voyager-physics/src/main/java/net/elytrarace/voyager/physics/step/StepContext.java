package net.elytrarace.voyager.physics.step;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.exception.InvalidSimulationStateException;
import net.elytrarace.voyager.physics.math.MinecraftMath;
import net.elytrarace.voyager.physics.math.ViewVector;

/**
 * The per-tick values every {@link ElytraStep} reads, computed once before the first step runs.
 *
 * <p>Transcribed from the top of Vanilla's {@code updateFallFlyingMovement}. {@code moveHorLength}
 * and {@code liftForce} are derived from the velocity entering the tick and are not recomputed
 * between steps, matching Vanilla, which computes them once and reuses them across all four
 * branches.
 */
public record StepContext(
        Vec3 lookAngle, float leanAngle, double lookHorLength, double moveHorLength, double gravity,
        double liftForce) {

    public StepContext {
        if (!Double.isFinite(gravity) || gravity <= 0.0) {
            throw new InvalidSimulationStateException(
                    "gravity must be finite and > 0, was %s".formatted(gravity));
        }
    }

    public static StepContext of(Vec3 velocity, float pitch, float yaw, double gravity) {
        Vec3 lookAngle = ViewVector.of(pitch, yaw);
        float leanAngle = pitch * MinecraftMath.DEG_TO_RAD;
        double lookHorLength = Math.sqrt(lookAngle.x() * lookAngle.x() + lookAngle.z() * lookAngle.z());
        double moveHorLength = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
        double liftForce = MinecraftMath.square(Math.cos(leanAngle));
        return new StepContext(lookAngle, leanAngle, lookHorLength, moveHorLength, gravity, liftForce);
    }
}
