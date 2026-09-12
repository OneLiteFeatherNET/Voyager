package net.elytrarace.voyager.physics.collision;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.physics.math.ViewVector;
import net.elytrarace.voyager.physics.step.ElytraStep;
import net.elytrarace.voyager.physics.step.StepContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures whether flattening a block's collision shape into separate {@link CollisionSpace} boxes
 * can move a trajectory, against keeping each block's boxes together the way Vanilla's
 * {@code Shapes.collide} does.
 *
 * <p>The question is not rhetorical and was open when this was written: the difference had been
 * derived from the source and never observed. Vanilla's snap-to-zero guard sits at the top of a loop
 * over {@code VoxelShape}s, so a two-box block is one guard check; {@link MovementResolver} loops
 * over boxes, so the same block is two. See {@link ShapeGroupedResolver}.
 *
 * <p>What the tests below establish, in order: the two algorithms are the same algorithm when every
 * shape is a single box (so a divergence over stairs is attributable to grouping and not to a
 * transcription slip); a divergence is constructible, so the mechanism is real; and no flight in a
 * dense sweep over a staircase produces one.
 */
class ShapeFlatteningMeasurementTest {

    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.8;
    private static final double GRAVITY = 0.08;

    /**
     * The residual the two algorithms can disagree about: small enough to trip Vanilla's
     * {@code 1.0E-7} guard, large enough to be a different number from zero.
     */
    private static final double RESIDUAL = 5.0e-8;

    /** Vanilla's oak-stairs collision shape, relative to its own cell: lower half, then upper step. */
    private static final List<double[]> STAIRS = List.of(
            new double[] {0.0, 0.0, 0.0, 1.0, 0.5, 1.0},
            new double[] {0.0, 0.5, 0.0, 1.0, 1.0, 0.5});

    private static final List<double[]> FULL_CUBE = List.of(new double[] {0.0, 0.0, 0.0, 1.0, 1.0, 1.0});

    /** A stone platform at y = -1, topped by a field of stairs at y = 0. */
    private static final List<List<Aabb>> STAIR_WORLD = world(true);

    /** The same ground, with the stairs replaced by full blocks — every shape a single box. */
    private static final List<List<Aabb>> CUBE_WORLD = world(false);

    @Test
    void theTwoResolversAreTheSameAlgorithmWhenEveryShapeIsOneBox() {
        // The self-check the measurement rests on. With singleton shapes the guard fires in the same
        // places in both, so any disagreement here would be a transcription error in
        // ShapeGroupedResolver rather than a property of grouping.
        Sweep sweep = flySweep(CUBE_WORLD);

        assertThat(sweep.divergentFlights()).isEmpty();
        assertThat(sweep.ticks()).isGreaterThan(10_000);
        assertThat(sweep.ticksWithCandidates()).isGreaterThan(20_000);
        assertThat(sweep.ticksWithAMultiBoxShape())
                .as("a world of full blocks has no multi-box shape at all — if this were non-zero, "
                        + "the two worlds would not be the controlled comparison they are meant to be")
                .isZero();
        assertThat(sweep.ticksWithAVerticalCollision()).isGreaterThan(20_000);
        assertThat(sweep.ticksWithAHorizontalCollision()).isGreaterThan(5_000);
    }

    @Test
    void noTrajectoryOverAStaircaseDiffersBetweenFlattenedAndGroupedShapes() {
        Sweep sweep = flySweep(STAIR_WORLD);

        // The counters are the test. Without them a green assertion on "no divergence" is equally
        // consistent with "the racer never reached the stairs", which is the failure mode this
        // repository has shipped before.
        assertThat(sweep.ticks()).isEqualTo(36_000);
        assertThat(sweep.ticksWithCandidates()).isGreaterThan(20_000);
        assertThat(sweep.ticksWithAMultiBoxShape())
                .as("ticks where one block contributed two boxes, i.e. where grouping could matter "
                        + "at all — the arrangement this measurement first used scored zero here")
                .isGreaterThan(20_000);
        assertThat(sweep.ticksWithAVerticalCollision()).isGreaterThan(20_000);
        assertThat(sweep.ticksWithAHorizontalCollision())
                .as("ticks where the racer was clamped against the wall, not merely resting on the floor")
                .isGreaterThan(5_000);

        assertThat(sweep.divergentFlights()).isEmpty();
    }

    @Test
    void aDivergenceIsConstructibleOnX() {
        // The mechanism, shown rather than argued for. The moving box stops 5e-8 short of a stairs
        // block and is placed at a height where only the block's lower box can clamp it. That box
        // leaves the residual; then the two algorithms part company. Flattened, the block's upper
        // box is a fresh candidate and the guard turns the residual into exactly 0 — the guard
        // precedes the overlap test, so a box that cannot clamp anything still trips it. Grouped, it
        // is the same shape, the guard is not consulted again, and the residual survives.
        Aabb mover = new Aabb(new Vec3(-1.0, 0.1, 0.1), new Vec3(-RESIDUAL, 0.4, 0.4));
        Vec3 movement = new Vec3(0.5, 0.0, 0.0);

        Divergence divergence = compare(mover, movement);

        assertThat(divergence.flattened().allowedMovement().x()).isZero();
        assertThat(divergence.grouped().allowedMovement().x()).isEqualTo(RESIDUAL);
        // Both still report the collision: the flag compares the final movement against the
        // requested 0.5, and 5e-8 is nowhere near Vanilla's 1e-5 horizontal tolerance.
        assertThat(divergence.flattened().xCollision()).isTrue();
        assertThat(divergence.grouped().xCollision()).isTrue();
    }

    @Test
    void aDivergenceIsConstructibleOnY() {
        // Descending onto the lower box's top face from 5e-8 above it, off to the side in z so that
        // the block's upper box overlaps on neither z nor y and can only act as a guard trip.
        Aabb mover = new Aabb(new Vec3(0.1, 0.5 + RESIDUAL, 0.6), new Vec3(0.4, 2.3, 0.9));
        Vec3 movement = new Vec3(0.0, -0.5, 0.0);

        // 0.5 + 5e-8 is not representable, so the clamp leaves very nearly, but not exactly,
        // -5e-8. Naming the arithmetic rather than the rounded intent keeps the assertion exact.
        double expected = 0.5 - (0.5 + RESIDUAL);
        assertThat(expected).isNotZero();
        assertThat(Math.abs(expected)).isLessThan(1.0e-7);

        Divergence divergence = compare(mover, movement);

        assertThat(divergence.flattened().allowedMovement().y()).isZero();
        assertThat(divergence.grouped().allowedMovement().y()).isEqualTo(expected);
        assertThat(divergence.flattened().verticalCollision()).isTrue();
        assertThat(divergence.grouped().verticalCollision()).isTrue();
    }

    @Test
    void aDivergenceIsConstructibleOnZ() {
        // The same shape again, approached along +z at a height inside the lower box and below the
        // upper one.
        Aabb mover = new Aabb(new Vec3(0.1, 0.05, -1.0), new Vec3(0.4, 0.4, -RESIDUAL));
        Vec3 movement = new Vec3(0.0, 0.0, 0.5);

        Divergence divergence = compare(mover, movement);

        assertThat(divergence.flattened().allowedMovement().z()).isZero();
        assertThat(divergence.grouped().allowedMovement().z()).isEqualTo(RESIDUAL);
        assertThat(divergence.flattened().zCollision()).isTrue();
        assertThat(divergence.grouped().zCollision()).isTrue();
    }

    private record Divergence(MovementResult flattened, MovementResult grouped) {
    }

    private static Divergence compare(Aabb mover, Vec3 movement) {
        List<Aabb> stairs = shapeAt(0, 0, 0, STAIRS);
        return new Divergence(
                MovementResolver.resolve(mover, movement, region -> stairs),
                ShapeGroupedResolver.resolve(mover, movement, region -> List.of(stairs)));
    }

    @Test
    void thatSameConstructionDoesNotDivergeWhenTheBlockIsASingleBox() {
        // The control for the three tests above: with one box there is no second candidate to trip
        // the guard, so the residual survives in both. This is what pins the divergence on grouping
        // rather than on the residual merely being small.
        Aabb mover = new Aabb(new Vec3(-1.0, 0.1, 0.1), new Vec3(-RESIDUAL, 0.4, 0.4));
        Vec3 movement = new Vec3(0.5, 0.0, 0.0);
        List<Aabb> cube = shapeAt(0, 0, 0, FULL_CUBE);

        MovementResult flattened = MovementResolver.resolve(mover, movement, region -> cube);
        MovementResult grouped = ShapeGroupedResolver.resolve(mover, movement, region -> List.of(cube));

        assertThat(flattened.allowedMovement().x()).isEqualTo(RESIDUAL);
        assertThat(grouped.allowedMovement().x()).isEqualTo(RESIDUAL);
    }

    // ---------------------------------------------------------------- the sweep

    /**
     * Launch directions. Three, because the axis-separated sweep resolves X before Z or Z before X
     * depending on which component is larger, and a sweep that only ever flew along one axis left
     * that branch untested — see {@link #world(boolean)}.
     */
    private record Heading(Vec3 velocity, float yaw, float pitch) {
    }

    private static final List<Heading> HEADINGS = List.of(
            new Heading(new Vec3(1.15, -0.02, 0.05), -90.0F, -5.0F),
            new Heading(new Vec3(0.05, -0.02, 1.15), 0.0F, 2.0F),
            new Heading(new Vec3(0.82, -0.02, 0.82), -45.0F, 9.0F));

    private record Sweep(int ticks,
                         int ticksWithCandidates,
                         int ticksWithAMultiBoxShape,
                         int ticksWithAVerticalCollision,
                         int ticksWithAHorizontalCollision,
                         List<String> divergentFlights) {
    }

    /**
     * Flies the same glide from many sub-block offsets, under both resolvers, and compares the two
     * trajectories state by state. The offsets are irrational-looking on purpose: a sweep on
     * round numbers samples the same few positions relative to a block face over and over.
     */
    private static Sweep flySweep(List<List<Aabb>> world) {
        int ticks = 0;
        int withCandidates = 0;
        int withMultiBox = 0;
        int withVertical = 0;
        int withHorizontal = 0;
        List<String> divergent = new ArrayList<>();

        for (int xi = 0; xi < 15; xi++) {
            for (int yi = 0; yi < 8; yi++) {
                for (int hi = 0; hi < HEADINGS.size(); hi++) {
                    for (int zi = 0; zi < 4; zi++) {
                        Heading heading = HEADINGS.get(hi);
                        double startX = -4.0 + xi * 0.0173;
                        double startY = 0.05 + yi * 0.0131;
                        double startZ = -3.0 + zi * 0.2531 + hi * 0.0417;
                        float pitch = heading.pitch();
                        FlightState flat = new FlightState(
                                new Vec3(startX, startY, startZ), heading.velocity(),
                                heading.yaw(), pitch, false);
                        FlightState grouped = flat;
                        FlightInput input = new FlightInput(heading.yaw(), pitch, false, 0, GRAVITY);

                        RecordingShapeSpace space = new RecordingShapeSpace(world);
                        for (int tick = 0; tick < 25; tick++) {
                            ticks++;
                            flat = ElytraSimulator.tick(flat, input, flattenedSpace(world));
                            Step step = tickGrouped(grouped, input, space);
                            grouped = step.state();

                            // Counted from the query the resolver itself made, not from a separate
                            // probe: the candidate set the measurement depends on is the one the
                            // swept region produced.
                            if (!space.last().isEmpty()) {
                                withCandidates++;
                            }
                            if (space.last().stream().anyMatch(shape -> shape.size() > 1)) {
                                withMultiBox++;
                            }
                            MovementResult result = step.result();
                            if (result.verticalCollision()) {
                                withVertical++;
                            }
                            if (result.horizontalCollision()) {
                                withHorizontal++;
                            }

                            if (!flat.equals(grouped)) {
                                divergent.add("start (%s, %s, %s) pitch %s tick %s: %s vs %s"
                                        .formatted(startX, startY, startZ, pitch, tick, flat, grouped));
                                break;
                            }
                        }
                    }
                }
            }
        }
        return new Sweep(ticks, withCandidates, withMultiBox, withVertical, withHorizontal, divergent);
    }

    private record Step(FlightState state, MovementResult result) {
    }

    /** A {@link ShapeGroupedResolver.ShapeSpace} that remembers what it last handed back. */
    private static final class RecordingShapeSpace implements ShapeGroupedResolver.ShapeSpace {

        private final List<List<Aabb>> world;
        private List<List<Aabb>> last = List.of();

        private RecordingShapeSpace(List<List<Aabb>> world) {
            this.world = world;
        }

        @Override
        public List<List<Aabb>> shapesIntersecting(Aabb region) {
            last = ShapeFlatteningMeasurementTest.shapesIntersecting(world, region);
            return last;
        }

        private List<List<Aabb>> last() {
            return last;
        }
    }

    /** {@link ElytraSimulator#tick} with the one substitution this measurement is about. */
    private static Step tickGrouped(FlightState previous, FlightInput input,
                                    ShapeGroupedResolver.ShapeSpace space) {
        Vec3 enteringVelocity = previous.velocity();
        if (input.fireworkBoostActive()) {
            Vec3 look = ViewVector.of(input.pitch(), input.yaw());
            enteringVelocity = new Vec3(
                    enteringVelocity.x() + look.x() * 0.1 + (look.x() * 1.5 - enteringVelocity.x()) * 0.5,
                    enteringVelocity.y() + look.y() * 0.1 + (look.y() * 1.5 - enteringVelocity.y()) * 0.5,
                    enteringVelocity.z() + look.z() * 0.1 + (look.z() * 1.5 - enteringVelocity.z()) * 0.5);
        }
        StepContext context = StepContext.of(enteringVelocity, input.pitch(), input.yaw(), input.gravity());
        Vec3 velocity = enteringVelocity;
        for (ElytraStep step : ElytraStep.stepsInOrder()) {
            velocity = step.step().apply(velocity, context);
        }
        MovementResult result =
                ShapeGroupedResolver.resolve(boundingBoxAt(previous.position()), velocity, space);
        Vec3 restituted = new Vec3(
                result.xCollision() ? 0.0 : velocity.x(),
                result.verticalCollision() ? 0.0 : velocity.y(),
                result.zCollision() ? 0.0 : velocity.z());
        return new Step(new FlightState(
                previous.position().plus(result.allowedMovement()),
                restituted, input.yaw(), input.pitch(), result.onGround()), result);
    }

    // ---------------------------------------------------------------- the world

    private static CollisionSpace flattenedSpace(List<List<Aabb>> world) {
        return region -> shapesIntersecting(world, region).stream().flatMap(List::stream).toList();
    }

    private static ShapeGroupedResolver.ShapeSpace groupedSpace(List<List<Aabb>> world) {
        return region -> shapesIntersecting(world, region);
    }

    /** The same per-box filter {@code MinestomCollisionSpace} applies, with the grouping kept. */
    private static List<List<Aabb>> shapesIntersecting(List<List<Aabb>> world, Aabb region) {
        List<List<Aabb>> matched = new ArrayList<>();
        for (List<Aabb> shape : world) {
            List<Aabb> boxes = shape.stream().filter(region::intersects).toList();
            if (!boxes.isEmpty()) {
                matched.add(boxes);
            }
        }
        return matched;
    }

    /**
     * A stone floor with two walls standing on it, meeting in an inside corner at {@code (3, ., 2)}.
     *
     * <p>Both details were forced by a mutation that survived the first version of this world:
     *
     * <ul>
     *   <li><b>Walls, not a floor.</b> A racer's box is 1.8 tall, so flying into a vertical face
     *       puts several of the wall's blocks in the swept region at once, and both boxes of each.
     *       Stairs laid flat as a floor are only ever touched on the box the racer lands on, and the
     *       multi-box counter scored zero — the measurement had nothing to measure.</li>
     *   <li><b>Two of them, plus a free-standing pillar.</b> With a single wall perpendicular to X,
     *       no flight is ever clamped on Z, and inverting {@link ShapeGroupedResolver}'s X/Z
     *       axis-order tie-break changed no trajectory at all. Adding the inside corner was not
     *       enough either — there the two orders commute. The pillar's outside corner is what makes
     *       the tie-break load-bearing, and so what lets the full-block sweep serve as a self-check
     *       on the reference resolver.</li>
     * </ul>
     */
    private static List<List<Aabb>> world(boolean stairsInTheWalls) {
        List<double[]> wall = stairsInTheWalls ? STAIRS : FULL_CUBE;
        List<List<Aabb>> blocks = new ArrayList<>();
        for (int z = -5; z <= 5; z++) {
            for (int x = -6; x <= 6; x++) {
                blocks.add(shapeAt(x, -1, z, FULL_CUBE));
            }
        }
        for (int y = 0; y <= 4; y++) {
            for (int z = -4; z <= 2; z++) {
                blocks.add(shapeAt(3, y, z, wall));
            }
            for (int x = -6; x <= 2; x++) {
                blocks.add(shapeAt(x, y, 2, wall));
            }
            // A free-standing pillar, clipped diagonally on the way to the corner. An inside corner
            // alone does not make the X/Z order observable: there, clamping one axis never changes
            // whether the other overlaps, so the two orders commute. An outside corner does — a box
            // that slips past on X can no longer slip past on Z once it has moved.
            blocks.add(shapeAt(0, y, 0, wall));
        }
        return List.copyOf(blocks);
    }

    private static List<Aabb> shapeAt(int x, int y, int z, List<double[]> boxes) {
        return boxes.stream()
                .map(box -> new Aabb(
                        new Vec3(box[0] + x, box[1] + y, box[2] + z),
                        new Vec3(box[3] + x, box[4] + y, box[5] + z)))
                .toList();
    }

    private static Aabb boundingBoxAt(Vec3 position) {
        return new Aabb(
                new Vec3(position.x() - HALF_WIDTH, position.y(), position.z() - HALF_WIDTH),
                new Vec3(position.x() + HALF_WIDTH, position.y() + HEIGHT, position.z() + HALF_WIDTH));
    }
}
