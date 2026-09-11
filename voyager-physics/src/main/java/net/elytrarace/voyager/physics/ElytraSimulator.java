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
 * <p><b>The tick runs in Vanilla's order: deadzone, steps, move, restitution, firework boost.</b>
 * Two of those five are outside {@code updateFallFlyingMovement} and neither is optional.
 *
 * <p>{@link #applyMovementDeadzone} is {@code LivingEntity.aiStep()}'s {@code 0.003} clamp, which
 * runs before {@code travel} and therefore before the first {@link ElytraStep} sees the velocity.
 *
 * <p>{@link #applyFireworkBoost} is {@code FireworkRocketEntity.tick()}'s impulse on the entity it
 * is attached to, and it runs <em>after</em> the move, not before the steps. A rocket is an entity:
 * it is spawned after the glider, so {@code EntityTickList} — insertion-ordered — ticks it after the
 * glider on the same level tick, when the glider has already run {@code updateFallFlyingMovement}
 * and {@code move} for that tick. The impulse therefore lands on the velocity the glider carries
 * <em>into the next tick</em>, and the position the glider reaches on a boosted tick is computed
 * from the velocity it entered that tick with — not from a freshly boosted one.
 *
 * <p>An earlier revision applied the boost first, reading "Vanilla applies it outside {@code
 * updateFallFlyingMovement}" as "before". It is outside on the far side. The two orders apply the
 * same number of impulses over a burn, so a free-running replay merely looks scaled; a one-step
 * residual against the E2a fixtures separates them outright — boost-first missed the recorded
 * position by {@code 0.69} blocks on {@code single-boost}'s first boosted tick and stayed {@code
 * 1.1e-02} out for the rest of the burn, while boost-last reproduces every boosted tick's position
 * bit-for-bit. See {@code VanillaParityTest} and {@code docs/reference/elytra-physics-26.2.md}.
 *
 * <p>The entity's bounding box — {@code 0.6F} blocks wide, {@code 1.8F} blocks tall, feet at the
 * entity's position — is derived here from {@link FlightState#position()} rather than carried in
 * {@link FlightState}, because it is a constant of the entity, not part of its state. Both
 * dimensions are {@code float} and are widened to {@code double} the way {@code
 * EntityDimensions.makeBoundingBox} widens them, which is fidelity trap 2 applied to the box rather
 * than to the formula: see {@link #HALF_WIDTH}.
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
 * and {@link MovementResult#zCollision()} — the same three flags {@code Entity.move} hands to
 * {@code restituteMovementAfterCollisions}, and with the same tolerances: the two horizontal ones
 * carry Vanilla's {@code Mth.equal} window of {@code 1.0E-5F}, the vertical one is exact
 * ({@code delta.y != movement.y}). A horizontal clamp inside {@code (0, 1.0E-5)} is therefore not a
 * collision for restitution's purposes and the velocity on that axis survives it, which is what
 * Vanilla does. {@link MovementResult#horizontalCollision()} is the derived {@code x || z} flag
 * Vanilla gates its block-damage check on; this simulator reports it but does no damage.
 *
 * <p><b>Only the middle of {@code travelFallFlying} is assembled here.</b> Vanilla's method has
 * three parts this does not, and the first two appear nowhere in this module at all:
 *
 * <ul>
 *   <li>{@code onClimbable()} — a gliding entity that touches a ladder or vine takes the other
 *       branch entirely: {@code travelInAir(input)} instead of {@code updateFallFlyingMovement},
 *       which is a different formula, not a modifier on this one.</li>
 *   <li>{@code stopFallFlying()} — called on that same branch; gliding ends and the shared flag is
 *       toggled. Nothing here ever ends a glide; the caller decides when to stop calling
 *       {@link #tick}.</li>
 *   <li>{@code handleFallFlyingCollisions(lastSpeed, newSpeed)} — collision damage,
 *       {@code (float)(diff * 10.0 - 3.0)} from the horizontal speed lost during the move, applied
 *       only when {@code horizontalCollision} is set and only server-side. This simulator computes
 *       the flag it is gated on and reports it, but does no damage.</li>
 * </ul>
 *
 * <p>The bookkeeping around {@code Entity.move} is likewise absent: the stuck-speed multiplier
 * (cobwebs, powder snow), {@code maybeBackOffFromEdge}, fall-distance tracking and fall damage, the
 * closing {@code getBlockSpeedFactor()} multiply (soul sand, honey), and the guard that skips the
 * position update outright when the resolved movement is below {@code 1.0E-7} and far short of what
 * was requested. See {@link MovementResolver}'s javadoc for what the sweep itself omits.
 *
 * <p>Position is derived as {@code previous.position().plus(allowedMovement)} and the bounding box
 * is rebuilt from it each tick, never carried. That is Vanilla's own direction of derivation:
 * {@code Entity.move} computes {@code Vec3 newPosition = pos.add(movement)} and hands it to
 * {@code setPos}, which then rebuilds the box via {@code makeBoundingBox()} from the new position.
 * The position is the authoritative value on both sides, so no rounding difference arises from
 * choosing one over the other.
 */
public abstract class ElytraSimulator {

    /** Vanilla's entity width, a {@code float} in {@code EntityDimensions}. */
    private static final float WIDTH = 0.6F;

    /** Vanilla's entity height, a {@code float} in {@code EntityDimensions}. */
    private static final float HEIGHT = 1.8F;

    /**
     * Half the entity width, computed in {@code float} and widened — <b>not</b> the {@code double}
     * literal {@code 0.3}.
     *
     * <p>{@code EntityDimensions.makeBoundingBox} is
     * {@code float f = this.width() / 2.0F; return new AABB(x - (double) f, …)}: the halving happens
     * in {@code float}, so the value that reaches the box is
     * {@code 0.300000011920928955078125}, {@code 1.19e-08} above {@code 0.3}. That difference is
     * invisible in free flight and decides the resting position against a wall exactly:
     * {@code wall-graze} records its final z as {@code 89.699999988079071}, not {@code 89.7}, and a
     * {@code double} {@code 0.3} reproduces the recorded velocity bit-for-bit while missing the
     * recorded position by that same {@code 1.19e-08} — the whole of the residual left in that
     * fixture once the boost order was corrected.
     *
     * <p>Whether the entity really is {@code 0.6F} wide is the platform layer's business; this
     * module only insists that whatever width it is arrives through {@code float} arithmetic.
     */
    private static final double HALF_WIDTH = WIDTH / 2.0F;

    /**
     * The box height, widened from {@link #HEIGHT} the way {@code makeBoundingBox} widens it
     * ({@code y + (double) g}), so it is {@code 1.7999999523162842} rather than {@code 1.8}.
     *
     * <p>Unlike {@link #HALF_WIDTH}, no fixture pins this: {@code wall-graze}'s wall spans
     * {@code y=250..310} and {@code landing}'s floor is under the feet, so no recording ever brings
     * the top of the box into contact with anything. It is widened for the same reason regardless —
     * a box assembled half in {@code float} and half in {@code double} is a rounding path Vanilla
     * does not have. The E2a fixtures were recorded from a zombie ({@code 1.95F} tall), so a future
     * ceiling-contact fixture would have to be recorded from the entity the game actually flies
     * before it could pin a player's {@code 1.8F}.
     */
    private static final double BOX_HEIGHT = HEIGHT;

    /**
     * {@code LivingEntity.aiStep()}'s per-axis movement deadzone. Any component whose magnitude is
     * under this is set to exactly zero before {@code travel} runs.
     */
    private static final double MOVEMENT_DEADZONE = 0.003;

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
        Vec3 enteringVelocity = applyMovementDeadzone(previous.velocity());

        StepContext context = StepContext.of(enteringVelocity, input.pitch(), input.yaw(), input.gravity());

        Vec3 velocity = enteringVelocity;
        Map<ElytraStep, Vec3> velocityAfter = new EnumMap<>(ElytraStep.class);
        for (ElytraStep step : ElytraStep.stepsInOrder()) {
            velocity = step.step().apply(velocity, context);
            velocityAfter.put(step, velocity);
        }

        Aabb box = boundingBoxAt(previous.position());
        MovementResult movementResult = MovementResolver.resolve(box, velocity, space);
        Vec3 outgoingVelocity = restitute(velocity, movementResult);

        if (input.fireworkBoostActive()) {
            outgoingVelocity = applyFireworkBoost(outgoingVelocity, ViewVector.of(input.pitch(), input.yaw()));
        }

        FlightState result = new FlightState(
                previous.position().plus(movementResult.allowedMovement()),
                outgoingVelocity,
                input.yaw(),
                input.pitch(),
                movementResult.onGround());

        return new TickTrace(result, velocityAfter);
    }

    /**
     * Zeroes each velocity component whose magnitude is under {@link #MOVEMENT_DEADZONE} —
     * {@code LivingEntity.aiStep()}, which runs before {@code travel} and therefore before the
     * first {@link ElytraStep}:
     *
     * <pre>{@code
     * Vec3 movement = this.getDeltaMovement();
     * double x = movement.x;
     * double y = movement.y;
     * double z = movement.z;
     * if (Math.abs(movement.x) < 0.003) { x = 0.0; }
     * if (Math.abs(movement.y) < 0.003) { y = 0.0; }
     * if (Math.abs(movement.z) < 0.003) { z = 0.0; }
     * this.setDeltaMovement(x, y, z);
     * }</pre>
     *
     * <p>It is not a rounding tidy-up and it is not negligible. {@code 0.003} is three orders above
     * the bounds this module is measured against, and clamping a component changes which branches
     * of {@code updateFallFlyingMovement} fire on that tick: a {@code y} of {@code -0.002} becomes
     * {@code 0.0}, which closes {@code DOWNWARD_GLIDE}'s {@code velocity.y < 0.0} guard, and a
     * clamped {@code x} or {@code z} changes {@code moveHorLength} for every remaining step. That is
     * why its effect concentrates at manoeuvre transitions, where a component crosses zero:
     * omitting it left {@code sustained-turn}, {@code pitch-extremes} and {@code
     * dive-and-pull-out} each with a burst of {@code 2–3e-03} deviations around the transition and a
     * lone outlier elsewhere, and reinstating it made all three bit-exact.
     *
     * <p>The clamp is per axis, against the incoming component, and it does not look at the
     * resulting vector's length: a velocity of {@code (0.002, 0.0, 0.002)} is zeroed on both axes
     * even though its horizontal length exceeds {@code 0.003}.
     */
    private static Vec3 applyMovementDeadzone(Vec3 velocity) {
        return new Vec3(
                Math.abs(velocity.x()) < MOVEMENT_DEADZONE ? 0.0 : velocity.x(),
                Math.abs(velocity.y()) < MOVEMENT_DEADZONE ? 0.0 : velocity.y(),
                Math.abs(velocity.z()) < MOVEMENT_DEADZONE ? 0.0 : velocity.z());
    }

    /**
     * Zeroes each collided component of {@code velocity} — Vanilla's collision restitution at
     * {@code bounciness = 0.0}. See the class Javadoc for which flag each axis reads and why.
     */
    private static Vec3 restitute(Vec3 velocity, MovementResult movementResult) {
        return new Vec3(
                movementResult.xCollision() ? 0.0 : velocity.x(),
                movementResult.verticalCollision() ? 0.0 : velocity.y(),
                movementResult.zCollision() ? 0.0 : velocity.z());
    }

    /**
     * {@code FireworkRocketEntity.tick()}'s impulse on the entity it is attached to, applied to the
     * velocity leaving the tick — see the class javadoc for why it is applied there and not before
     * the steps.
     *
     * <pre>{@code
     * this.attachedToEntity.setDeltaMovement(
     *     movement.add(
     *         lookAngle.x * 0.1 + (lookAngle.x * 1.5 - movement.x) * 0.5,
     *         lookAngle.y * 0.1 + (lookAngle.y * 1.5 - movement.y) * 0.5,
     *         lookAngle.z * 0.1 + (lookAngle.z * 1.5 - movement.z) * 0.5));
     * }</pre>
     *
     * <p>The impulse is the whole argument to {@code Vec3.add}, so it is summed <em>first</em> and
     * added to the component once. The parentheses below are therefore load-bearing:
     * {@code v + a * 0.1 + (…) * 0.5} associates left to right and rounds twice against {@code v},
     * which differs from Vanilla in the last bits — small enough to pass a {@code 1e-12} tolerance
     * and large enough to stop a fixture being bit-exact.
     *
     * <p>Read as a whole the impulse is a blend, not an addition:
     * {@code v' = 0.5 · v + 0.85 · lookAngle}, which is the halfway point between the current
     * velocity and a terminal {@code 1.7 · lookAngle}. A burn therefore converges — {@code
     * single-boost} settles at {@code velZ = 1.6716} and holds it — rather than accelerating without
     * bound, and two rockets burning at once converge nearer the terminal value instead of doubling
     * the speed, which is what {@code chained-boosts} records ({@code 1.6862}).
     */
    private static Vec3 applyFireworkBoost(Vec3 velocity, Vec3 lookAngle) {
        return new Vec3(
                velocity.x() + (lookAngle.x() * 0.1 + (lookAngle.x() * 1.5 - velocity.x()) * 0.5),
                velocity.y() + (lookAngle.y() * 0.1 + (lookAngle.y() * 1.5 - velocity.y()) * 0.5),
                velocity.z() + (lookAngle.z() * 0.1 + (lookAngle.z() * 1.5 - velocity.z()) * 0.5));
    }

    private static Aabb boundingBoxAt(Vec3 position) {
        Vec3 min = new Vec3(position.x() - HALF_WIDTH, position.y(), position.z() - HALF_WIDTH);
        Vec3 max = new Vec3(position.x() + HALF_WIDTH, position.y() + BOX_HEIGHT, position.z() + HALF_WIDTH);
        return new Aabb(min, max);
    }
}
