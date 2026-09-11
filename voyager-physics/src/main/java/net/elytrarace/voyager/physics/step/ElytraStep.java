package net.elytrarace.voyager.physics.step;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.physics.math.MinecraftMath;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Vanilla's {@code updateFallFlyingMovement}, decomposed into its five named steps, in Vanilla's
 * order.
 *
 * <p>The decomposition is diagnostic: a trace mismatch can name the step that diverged instead of
 * only the tick. Each constant transcribes exactly one branch of the source formula, including its
 * guard, and reads {@code lookHorLength}, {@code moveHorLength} and {@code liftForce} from the
 * {@link StepContext} rather than recomputing them, matching Vanilla's single computation at the
 * top of the method.
 */
public enum ElytraStep {

    /**
     * {@code velocity.add(0.0, gravity * (-1.0 + liftForce * 0.75), 0.0)}. The {@code + 0.0} on x
     * and z is not a no-op: Vanilla's {@code Vec3.add} normalizes a {@code -0.0} component to
     * {@code +0.0}, and a trace comparison using {@code Double.equals} would see the difference.
     */
    GRAVITY_AND_LIFT((velocity, context) -> new Vec3(
            velocity.x() + 0.0,
            velocity.y() + context.gravity() * (-1.0 + context.liftForce() * 0.75),
            velocity.z() + 0.0)),

    /**
     * Converts sink into forward motion, scaled by {@code liftForce}, while descending and looking
     * somewhere with a horizontal component.
     */
    DOWNWARD_GLIDE((velocity, context) -> {
        if (velocity.y() < 0.0 && context.lookHorLength() > 0.0) {
            double convert = velocity.y() * -0.1 * context.liftForce();
            return velocity.plus(new Vec3(
                    context.lookAngle().x() * convert / context.lookHorLength(),
                    convert,
                    context.lookAngle().z() * convert / context.lookHorLength()));
        }
        return velocity;
    }),

    /**
     * Trades horizontal speed for climb while pitched below the horizon and looking somewhere with a
     * horizontal component. The vertical term is amplified {@code x3.2} relative to the horizontal
     * trade.
     */
    UPWARD_PITCH_BOOST((velocity, context) -> {
        if (context.leanAngle() < 0.0F && context.lookHorLength() > 0.0) {
            double convert = context.moveHorLength() * -MinecraftMath.sin(context.leanAngle()) * 0.04;
            return velocity.plus(new Vec3(
                    -context.lookAngle().x() * convert / context.lookHorLength(),
                    convert * 3.2,
                    -context.lookAngle().z() * convert / context.lookHorLength()));
        }
        return velocity;
    }),

    /** Interpolates horizontal velocity towards the look direction at rate {@code 0.1}. */
    DIRECTION_ALIGNMENT((velocity, context) -> {
        if (context.lookHorLength() > 0.0) {
            return velocity.plus(new Vec3(
                    (context.lookAngle().x() / context.lookHorLength() * context.moveHorLength()
                            - velocity.x()) * 0.1,
                    0.0,
                    (context.lookAngle().z() / context.lookHorLength() * context.moveHorLength()
                            - velocity.z()) * 0.1));
        }
        return velocity;
    }),

    /**
     * {@code velocity.multiply(0.99F, 0.98F, 0.99F)}. The constants are {@code float} literals
     * widened to {@code double}, not the double literals {@code 0.99}/{@code 0.98}.
     */
    DRAG((velocity, context) -> new Vec3(
            velocity.x() * 0.99F, velocity.y() * 0.98F, velocity.z() * 0.99F));

    private static final ElytraStep[] VALUES = values();
    private static final List<ElytraStep> STEPS_IN_ORDER = Collections.unmodifiableList(Arrays.asList(VALUES));

    private final FlightStep step;

    ElytraStep(FlightStep step) {
        this.step = step;
    }

    public FlightStep step() {
        return step;
    }

    /**
     * The steps in Vanilla's order, without allocating a new array on every call the way {@link
     * #values()} does.
     */
    public static List<ElytraStep> stepsInOrder() {
        return STEPS_IN_ORDER;
    }
}
