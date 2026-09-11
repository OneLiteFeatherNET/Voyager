package net.elytrarace.voyager.physics.step;

import net.elytrarace.voyager.api.math.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behavioural contract every {@link FlightStep} must satisfy, run against all five
 * {@link ElytraStep} constants.
 *
 * <p>This is the mechanism the design names for Liskov substitutability. ArchUnit can check that a
 * type exists, implements an interface and lives in the right package; it cannot check that a new
 * implementation behaves like its siblings. A contract test can, and it is the only thing standing
 * between "the enum has five constants" and "the five are interchangeable where the pipeline treats
 * them as such": {@code ElytraSimulator} runs them in a loop, handing each the previous one's output
 * and the same {@link StepContext}, so any constant that diverged from these four properties would
 * break the loop rather than just itself.
 *
 * <p>Four properties, each chosen because breaking it breaks a caller:
 *
 * <ul>
 *   <li><b>Finite in, finite out.</b> The pipeline has no guard against a {@code NaN}: it would
 *       flow through the remaining steps, through {@code MovementResolver}'s comparisons (where
 *       every comparison against {@code NaN} is false, so no clamp fires) and into a position,
 *       silently. The sweep below includes the two degenerate rotations where a step's own
 *       {@code / lookHorLength} division is the danger.</li>
 *   <li><b>A pure function of its two arguments.</b> The design forbids state in this module
 *       outright; the loop in {@code ElytraSimulator} calls the same constant on many ticks and
 *       {@code tickTraced} calls it again for the same tick, so a step that remembered anything
 *       would make a traced tick differ from a plain one.</li>
 *   <li><b>A closed guard leaves the vector untouched.</b> Vanilla's branches are guarded, and a
 *       step that contributed anyway — even a signed zero — would change the result of a tick in
 *       which Vanilla does nothing. Each guarded constant supplies a case where its own guard is
 *       shut.</li>
 *   <li><b>An open guard changes the vector.</b> The counterweight to the previous one: without it
 *       a step that returned its argument unchanged under every input would pass three of these
 *       four, and pass the fourth too if its "guard closed" case were the only one checked.</li>
 * </ul>
 *
 * <p>{@link #everyStepIsHeldToTheContract} closes the last hole: a sixth constant added to
 * {@link ElytraStep} without a nested case here would otherwise leave the suite green while nothing
 * held the new step to anything.
 */
class FlightStepContractTest {

    /** Rotations the sweep runs, including both degenerate pitches. */
    private static final float[] PITCHES = {-90.0f, -70.0f, -20.0f, -5.0f, 0.0f, 5.0f, 37.0f, 90.0f};

    /** Yaws the sweep runs. Never only {@code 0}, where {@code lookAngle.x} vanishes. */
    private static final float[] YAWS = {-180.0f, -37.0f, 0.0f, 37.0f, 90.0f, 179.0f};

    /** Gravities the sweep runs: Vanilla's default and Slow Falling's clamp. */
    private static final double[] GRAVITIES = {0.08, 0.01};

    /** Velocities the sweep runs: rising, sinking, stationary, fast, and sub-deadzone. */
    private static final Vec3[] VELOCITIES = {
            Vec3.ZERO,
            new Vec3(0.0, -0.5, 0.5),
            new Vec3(0.6, -0.1, 0.8),
            new Vec3(-0.6, 0.4, -0.8),
            new Vec3(0.0, 1.2, 0.0),
            new Vec3(30.0, -30.0, 30.0),
            new Vec3(1.0e-12, -1.0e-12, 1.0e-12),
    };

    /**
     * Every {@link ElytraStep} constant is held to the contract — by identity, not by counting
     * nested classes, so a sixth constant paired with a nested case for a step that already had one
     * still fails.
     */
    @Test
    void everyStepIsHeldToTheContract() {
        List<ElytraStep> covered = Arrays.stream(FlightStepContractTest.class.getDeclaredClasses())
                .filter(Contract.class::isAssignableFrom)
                .filter(nested -> !Modifier.isAbstract(nested.getModifiers()))
                .map(this::instantiate)
                .map(Contract::step)
                .sorted()
                .toList();

        assertThat(covered)
                .as("one nested contract case per ElytraStep constant")
                .containsExactly(ElytraStep.values());
    }

    private Contract instantiate(Class<?> nested) {
        try {
            return (Contract) nested.getDeclaredConstructor(FlightStepContractTest.class).newInstance(this);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "%s must be an inner class of FlightStepContractTest with the implicit "
                            + "constructor".formatted(nested.getSimpleName()), exception);
        }
    }

    /** Every {@code (velocity, context)} pair the finiteness and purity sweeps run. */
    private static List<Object[]> sweep() {
        List<Object[]> samples = new ArrayList<>();
        for (Vec3 velocity : VELOCITIES) {
            for (float pitch : PITCHES) {
                for (float yaw : YAWS) {
                    for (double gravity : GRAVITIES) {
                        samples.add(new Object[] {
                                velocity, StepContext.of(velocity, pitch, yaw, gravity),
                                "velocity %s pitch %s yaw %s gravity %s".formatted(velocity, pitch, yaw, gravity)});
                    }
                }
            }
        }
        return samples;
    }

    abstract class Contract {

        /** The constant under test. */
        abstract ElytraStep step();

        /**
         * Every distinct way this step's guard can be shut — empty for a step Vanilla applies
         * unconditionally ({@code GRAVITY_AND_LIFT} and {@code DRAG}). An empty list is a claim,
         * not an exemption: {@link #aClosedGuardLeavesTheVectorUntouched} allows it only for those
         * two constants, and {@link #anOpenGuardChangesTheVector} still holds every step to doing
         * something.
         */
        abstract List<Case> closedGuardCases();

        /** A case in which this step's guard does hold and its contribution is non-zero. */
        abstract Case guardOpen();

        @Test
        void finiteInputNeverProducesANonFiniteVector() {
            List<String> offenders = new ArrayList<>();
            for (Object[] sample : sweep()) {
                Vec3 result = step().step().apply((Vec3) sample[0], (StepContext) sample[1]);
                if (!Double.isFinite(result.x()) || !Double.isFinite(result.y())
                        || !Double.isFinite(result.z())) {
                    offenders.add("%s -> %s".formatted(sample[2], result));
                }
            }
            assertThat(offenders).as("%s produced a non-finite vector", step()).isEmpty();
        }

        @Test
        void theStepIsAPureFunctionOfItsTwoArguments() {
            List<Object[]> samples = sweep();
            List<Vec3> first = samples.stream()
                    .map(sample -> step().step().apply((Vec3) sample[0], (StepContext) sample[1]))
                    .toList();

            // Re-run in reverse, so every call is preceded by a different one: a step caching its
            // last argument pair would still pass a straight second pass in the same order.
            for (int index = samples.size() - 1; index >= 0; index--) {
                Object[] sample = samples.get(index);
                Vec3 velocity = (Vec3) sample[0];
                StepContext context = (StepContext) sample[1];
                Vec3 again = step().step().apply(velocity, context);

                assertThat(again).as("%s is not pure at %s", step(), sample[2]).isEqualTo(first.get(index));
                assertThat(velocity).as("%s mutated its velocity argument", step()).isEqualTo(sample[0]);
                assertThat(context).as("%s mutated its context argument", step()).isEqualTo(sample[1]);
            }
        }

        @Test
        void aClosedGuardLeavesTheVectorUntouched() {
            List<Case> closed = closedGuardCases();
            if (closed.isEmpty()) {
                assertThat(step())
                        .as("only Vanilla's unguarded branches may declare no closed-guard case")
                        .isIn(ElytraStep.GRAVITY_AND_LIFT, ElytraStep.DRAG);
                return;
            }
            for (Case shut : closed) {
                Vec3 result = step().step().apply(shut.velocity(), shut.context());

                assertThat(result)
                        .as("%s contributed with its guard closed: %s", step(), shut.why())
                        .isEqualTo(shut.velocity());
            }
        }

        @Test
        void anOpenGuardChangesTheVector() {
            Case open = guardOpen();
            Vec3 result = step().step().apply(open.velocity(), open.context());

            assertThat(result)
                    .as("%s did nothing with its guard open: %s", step(), open.why())
                    .isNotEqualTo(open.velocity());
        }
    }

    /** One input pair for a contract assertion, with the reason it is the pair it is. */
    record Case(Vec3 velocity, StepContext context, String why) {
    }

    private static StepContext contextOf(Vec3 velocity, float pitch, float yaw) {
        return StepContext.of(velocity, pitch, yaw, 0.08);
    }

    /**
     * A context whose {@code lookHorLength} is exactly zero, built through {@link StepContext}'s
     * canonical constructor rather than from a rotation.
     *
     * <p>No rotation produces it: {@code MinecraftMath.cos} reads a 65536-entry {@code float}
     * table, and at pitch {@code ±90} it returns roughly {@code 1e-8} rather than {@code 0}, so
     * {@code lookHorLength > 0.0} is still true there and the {@code / lookHorLength} divisions
     * still run — which is Vanilla's behaviour and deliberately not guarded against (see fidelity
     * trap 1 in {@code docs/reference/elytra-physics-26.2.md}). Constructing the context directly is
     * therefore the only way to exercise the guard itself, as opposed to the near-miss.
     */
    private static StepContext lookingStraightUp(Vec3 velocity, float pitch) {
        double moveHorLength = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
        float leanAngle = pitch * net.elytrarace.voyager.physics.math.MinecraftMath.DEG_TO_RAD;
        return new StepContext(new Vec3(0.0, 1.0, 0.0), leanAngle, 0.0, moveHorLength, 0.08,
                Math.cos(leanAngle) * Math.cos(leanAngle));
    }

    @Nested
    class GravityAndLift extends Contract {

        @Override
        ElytraStep step() {
            return ElytraStep.GRAVITY_AND_LIFT;
        }

        @Override
        List<Case> closedGuardCases() {
            return List.of(); // Vanilla applies gravity and lift unconditionally.
        }

        @Override
        Case guardOpen() {
            Vec3 velocity = new Vec3(0.0, -0.5, 0.5);
            return new Case(velocity, contextOf(velocity, -5.0f, 37.0f),
                    "gravity always moves y");
        }
    }

    @Nested
    class DownwardGlide extends Contract {

        @Override
        ElytraStep step() {
            return ElytraStep.DOWNWARD_GLIDE;
        }

        @Override
        List<Case> closedGuardCases() {
            Vec3 rising = new Vec3(0.0, 0.5, 0.5);
            Vec3 sinking = new Vec3(0.0, -0.5, 0.5);
            return List.of(
                    new Case(rising, contextOf(rising, -5.0f, 37.0f),
                            "velocity.y >= 0, so there is no sink to convert"),
                    new Case(sinking, lookingStraightUp(sinking, -90.0f),
                            "lookHorLength == 0, so the horizontal direction is undefined"));
        }

        @Override
        Case guardOpen() {
            Vec3 sinking = new Vec3(0.0, -0.5, 0.5);
            return new Case(sinking, contextOf(sinking, -5.0f, 37.0f),
                    "velocity.y < 0 and lookHorLength > 0");
        }
    }

    @Nested
    class UpwardPitchBoost extends Contract {

        @Override
        ElytraStep step() {
            return ElytraStep.UPWARD_PITCH_BOOST;
        }

        @Override
        List<Case> closedGuardCases() {
            Vec3 velocity = new Vec3(0.0, -0.5, 0.5);
            return List.of(
                    new Case(velocity, contextOf(velocity, 20.0f, 37.0f),
                            "leanAngle >= 0, so the entity is pitched at or below the horizon"),
                    new Case(velocity, lookingStraightUp(velocity, -90.0f),
                            "lookHorLength == 0, so there is no horizontal axis to trade against"));
        }

        @Override
        Case guardOpen() {
            Vec3 velocity = new Vec3(0.0, -0.5, 0.5);
            return new Case(velocity, contextOf(velocity, -20.0f, 37.0f),
                    "leanAngle < 0 and lookHorLength > 0");
        }
    }

    @Nested
    class DirectionAlignment extends Contract {

        @Override
        ElytraStep step() {
            return ElytraStep.DIRECTION_ALIGNMENT;
        }

        @Override
        List<Case> closedGuardCases() {
            Vec3 velocity = new Vec3(0.0, -0.5, 0.5);
            return List.of(new Case(velocity, lookingStraightUp(velocity, -90.0f),
                    "lookHorLength == 0, so there is no horizontal look direction to align to"));
        }

        @Override
        Case guardOpen() {
            Vec3 velocity = new Vec3(0.0, -0.5, 0.5);
            return new Case(velocity, contextOf(velocity, -5.0f, 37.0f),
                    "lookHorLength > 0 and the velocity is not already aligned");
        }
    }

    @Nested
    class Drag extends Contract {

        @Override
        ElytraStep step() {
            return ElytraStep.DRAG;
        }

        @Override
        List<Case> closedGuardCases() {
            return List.of(); // Vanilla multiplies by the drag constants unconditionally.
        }

        @Override
        Case guardOpen() {
            Vec3 velocity = new Vec3(0.0, -0.5, 0.5);
            return new Case(velocity, contextOf(velocity, -5.0f, 37.0f),
                    "drag always scales a non-zero velocity");
        }
    }
}
