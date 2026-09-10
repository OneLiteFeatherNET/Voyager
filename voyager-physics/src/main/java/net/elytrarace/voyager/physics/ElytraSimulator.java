package net.elytrarace.voyager.physics;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.collision.MovementResolver;
import net.elytrarace.voyager.physics.collision.MovementResult;
import net.elytrarace.voyager.physics.math.ViewVector;
import net.elytrarace.voyager.physics.step.ElytraStep;
import net.elytrarace.voyager.physics.step.StepContext;

import java.util.EnumMap;
import java.util.Map;

/**
 * Assembles Vanilla's {@code travelFallFlying} from the pieces built by earlier tasks: the firework
 * boost, the five ordered {@link ElytraStep}s, and block-collision resolution via
 * {@link MovementResolver}.
 *
 * <p>A pure function from an incoming {@link FlightState} and the tick's {@link FlightInput} to the
 * resulting {@link FlightState} — there is no configuration, no tuning constant and nothing to
 * inject. A later Vanilla version with different physics constants becomes a different constants
 * record passed through the pipeline, not a subclass of this class.
 *
 * <p>The firework boost is applied here, before the velocity ever reaches a {@link StepContext} or
 * an {@link ElytraStep}, because Vanilla applies it outside {@code updateFallFlyingMovement}: the
 * velocity {@code updateFallFlyingMovement} reads already includes that tick's boost. Applying it
 * after the steps instead would feed the unboosted velocity into every step's {@code moveHorLength}
 * and {@code liftForce} terms and diverge from Vanilla despite looking correct in isolation.
 *
 * <p>The entity's bounding box — 0.6 blocks wide, 1.8 blocks tall, feet at the entity's position —
 * is derived here from {@link FlightState#position()} rather than carried in {@link FlightState},
 * because it is a constant of the entity, not part of its state.
 *
 * <p>A collided axis is zeroed in the resulting velocity, never merely left at the pre-collision
 * value. This is Vanilla's {@code Entity.restituteMovementAfterCollisions}, called from {@code
 * Entity.move} right after the same axis-separated sweep {@link MovementResolver} performs: {@code
 * movementAfterBounce = movementAfterBounce.with(axis, -currentMovement * restitution)}, or the
 * equivalent vertical expression, evaluated per collided axis. {@code restitution} is {@code
 * Entity.getEntityBounciness()}, which is {@code 0.0} for every entity against every ordinary
 * (non-slime, non-bed) block — 26.2's default — so the term collapses to zero regardless of the
 * pre-collision velocity: a collided axis' velocity becomes exactly {@code 0.0}, not a fraction of
 * itself. Bouncy blocks are not modelled; if the simulation ever needs them, {@code restitution}
 * becomes a non-zero per-block value threaded in here, not a different formula.
 *
 * <p>Zeroing reads {@link MovementResult#xCollision()}, {@link MovementResult#verticalCollision()}
 * and {@link MovementResult#zCollision()} — the exact, non-tolerance flags — not {@link
 * MovementResult#horizontalCollision()}: Vanilla's own restitution acts on the resolver's actual
 * per-axis clamp, while {@code horizontalCollision} carries a {@code 1.0E-5F} tolerance meant for a
 * different concern (whether to report a collision at all for gameplay purposes, including the
 * block-damage check this simulator does not yet implement).
 */
public abstract class ElytraSimulator {

    /** Half of Vanilla's 0.6-block entity width, applied on both sides of the position. */
    private static final double HALF_WIDTH = 0.3;

    /** Vanilla's entity height, measured up from the feet at the position. */
    private static final double HEIGHT = 1.8;

    private ElytraSimulator() {
    }

    /**
     * Advances {@code previous} by one tick under {@code input}, resolving movement against
     * {@code space}. Production code calls this form.
     */
    public static FlightState tick(FlightState previous, FlightInput input, CollisionSpace space) {
        return tickTraced(previous, input, space).result();
    }

    /**
     * Advances {@code previous} by one tick exactly as {@link #tick} does, additionally recording
     * the velocity observed after each {@link ElytraStep} into the returned {@link TickTrace}. A
     * test harness replaying a recorded trace calls this form so a divergence can name the step, not
     * only the tick.
     */
    public static TickTrace tickTraced(FlightState previous, FlightInput input, CollisionSpace space) {
        Vec3 enteringVelocity = previous.velocity();
        if (input.fireworkBoostActive()) {
            Vec3 lookAngle = ViewVector.of(input.pitch(), input.yaw());
            enteringVelocity = applyFireworkBoost(enteringVelocity, lookAngle);
        }

        StepContext context = StepContext.of(enteringVelocity, input.pitch(), input.yaw(), input.gravity());

        Vec3 velocity = enteringVelocity;
        Map<ElytraStep, Vec3> velocityAfter = new EnumMap<>(ElytraStep.class);
        for (ElytraStep step : ElytraStep.stepsInOrder()) {
            velocity = step.step().apply(velocity, context);
            velocityAfter.put(step, velocity);
        }

        Aabb box = boundingBoxAt(previous.position());
        MovementResult movementResult = MovementResolver.resolve(box, velocity, space);
        Vec3 restitutedVelocity = restitute(velocity, movementResult);

        FlightState result = new FlightState(
                previous.position().plus(movementResult.allowedMovement()),
                restitutedVelocity,
                input.yaw(),
                input.pitch(),
                movementResult.onGround());

        return new TickTrace(result, velocityAfter);
    }

    /**
     * Zeroes each collided component of {@code velocity} — Vanilla's collision restitution at
     * {@code bounciness = 0.0}. See the class Javadoc for why the exact, non-tolerance flags are the
     * correct ones to read here.
     */
    private static Vec3 restitute(Vec3 velocity, MovementResult movementResult) {
        return new Vec3(
                movementResult.xCollision() ? 0.0 : velocity.x(),
                movementResult.verticalCollision() ? 0.0 : velocity.y(),
                movementResult.zCollision() ? 0.0 : velocity.z());
    }

    /**
     * {@code velocity += lookAngle * 0.1 + (lookAngle * 1.5 - velocity) * 0.5}, evaluated per axis —
     * transcribed from Vanilla's firework-boost impulse, applied outside
     * {@code updateFallFlyingMovement}.
     */
    private static Vec3 applyFireworkBoost(Vec3 velocity, Vec3 lookAngle) {
        return new Vec3(
                velocity.x() + lookAngle.x() * 0.1 + (lookAngle.x() * 1.5 - velocity.x()) * 0.5,
                velocity.y() + lookAngle.y() * 0.1 + (lookAngle.y() * 1.5 - velocity.y()) * 0.5,
                velocity.z() + lookAngle.z() * 0.1 + (lookAngle.z() * 1.5 - velocity.z()) * 0.5);
    }

    private static Aabb boundingBoxAt(Vec3 position) {
        Vec3 min = new Vec3(position.x() - HALF_WIDTH, position.y(), position.z() - HALF_WIDTH);
        Vec3 max = new Vec3(position.x() + HALF_WIDTH, position.y() + HEIGHT, position.z() + HALF_WIDTH);
        return new Aabb(min, max);
    }
}
