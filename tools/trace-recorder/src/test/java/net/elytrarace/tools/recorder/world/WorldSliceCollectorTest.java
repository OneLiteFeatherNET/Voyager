package net.elytrarace.tools.recorder.world;

import net.elytrarace.tools.recorder.format.BlockBox;
import net.elytrarace.tools.recorder.format.TraceTick;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorldSliceCollectorTest {

    private static TraceTick at(int index, double x, double y, double z) {
        return new TraceTick(index, x, y, z, 0, 0, 0, 0f, 0f, false, false, 0);
    }

    /** A floor filling y == 0, unbounded horizontally. */
    private static final SolidBlockSource FLOOR = (x, y, z) -> y == 0;

    private static final SolidBlockSource EMPTY = (x, y, z) -> false;

    /** A floor one block below the origin, unbounded horizontally. */
    private static final SolidBlockSource FLOOR_BELOW_ORIGIN = (x, y, z) -> y == -1;

    /** Solid at exactly one block, two blocks out on X from the origin block. */
    private static final SolidBlockSource EDGE_AT_TWO_BLOCKS = (x, y, z) -> x == 2 && y == 0 && z == 0;

    @Test
    void collectsNothingFromAnEmptyWorld() {
        assertThat(WorldSliceCollector.collect(List.of(at(0, 0, 5, 0)), 2.0, EMPTY)).isEmpty();
    }

    @Test
    void collectsTheFloorBlocksWithinTheRadius() {
        List<BlockBox> slice = WorldSliceCollector.collect(List.of(at(0, 0.5, 1.5, 0.5)), 1.0, FLOOR);

        // The window is named block by block, not counted. A count alone cannot tell
        // [-1, 1] from [0, 2] -- both are three columns per axis -- and the floor is
        // unbounded horizontally, so a shifted or asymmetric scan range would still
        // find nine solid blocks and still report y == 0 for every one of them.
        assertThat(slice).containsExactlyInAnyOrder(
                new BlockBox(-1, 0, -1, 0, 1, 0), new BlockBox(-1, 0, 0, 0, 1, 1), new BlockBox(-1, 0, 1, 0, 1, 2),
                new BlockBox(0, 0, -1, 1, 1, 0), new BlockBox(0, 0, 0, 1, 1, 1), new BlockBox(0, 0, 1, 1, 1, 2),
                new BlockBox(1, 0, -1, 2, 1, 0), new BlockBox(1, 0, 0, 2, 1, 1), new BlockBox(1, 0, 1, 2, 1, 2));
    }

    @Test
    void emitsUnitCubesAtBlockCoordinates() {
        List<BlockBox> slice = WorldSliceCollector.collect(List.of(at(0, 0.5, 0.5, 0.5)), 0.0, FLOOR);

        assertThat(slice).containsExactly(new BlockBox(0, 0, 0, 1, 1, 1));
    }

    @Test
    void deduplicatesBlocksSharedBetweenTicks() {
        List<TraceTick> path = List.of(at(0, 0.5, 0.5, 0.5), at(1, 0.6, 0.5, 0.5));

        List<BlockBox> slice = WorldSliceCollector.collect(path, 0.0, FLOOR);

        assertThat(slice).containsExactly(new BlockBox(0, 0, 0, 1, 1, 1));
    }

    @Test
    void coversEveryTickOfThePath() {
        List<TraceTick> path = List.of(at(0, 0.5, 0.5, 0.5), at(1, 8.5, 0.5, 0.5));

        List<BlockBox> slice = WorldSliceCollector.collect(path, 0.0, FLOOR);

        assertThat(slice).containsExactlyInAnyOrder(
                new BlockBox(0, 0, 0, 1, 1, 1),
                new BlockBox(8, 0, 0, 9, 1, 1));
    }

    @Test
    void flooringNegativePositionsRoundsDownRatherThanTowardsZero() {
        // Every other test sits at a positive coordinate, where Math.floor and an int
        // cast agree. They disagree below zero: (int) -0.5 is 0, Math.floor(-0.5) is -1.
        // Recordings fly through negative coordinates, so the wrong one shifts the whole
        // slice by a block on that side of the origin and the replay resolves the wrong
        // collisions.
        List<BlockBox> slice = WorldSliceCollector.collect(List.of(at(0, -0.5, 0.5, -3.25)), 0.0, FLOOR);

        assertThat(slice).containsExactly(new BlockBox(-1, 0, -4, 0, 1, -3));
    }

    @Test
    void flooringANegativeYAlsoRoundsDownRatherThanTowardsZero() {
        // Every test above that crosses the origin (radius aside) only does so on X or Z --
        // posY sits at 0.5 or higher everywhere else in this file. Math.floor and an int cast
        // agree above zero on every axis, so a per-axis flooring bug on Y specifically would
        // pass every other test here. X and Z stay positive so this isolates Y.
        List<BlockBox> slice = WorldSliceCollector.collect(List.of(at(0, 0.5, -0.5, 0.5)), 0.0, FLOOR_BELOW_ORIGIN);

        assertThat(slice).containsExactly(new BlockBox(0, -1, 0, 1, 0, 1));
    }

    @Test
    void scansExactlyTheCeilingOfAFractionalRadius() {
        // Every radius above is a whole number (0.0, 1.0, 2.0), where Math.ceil and a plain
        // (int) truncation agree. They disagree on a fractional radius: ceil(1.5) is 2,
        // (int) 1.5 is 1. A block exactly 2 out on X is only found if the true ceiling, not a
        // truncated one, drives the scan window.
        List<BlockBox> slice =
                WorldSliceCollector.collect(List.of(at(0, 0.5, 0.5, 0.5)), 1.5, EDGE_AT_TWO_BLOCKS);

        assertThat(slice).containsExactly(new BlockBox(2, 0, 0, 3, 1, 1));
    }
}
