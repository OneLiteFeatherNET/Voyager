package net.elytrarace.voyager.platform.collision;

import net.elytrarace.voyager.api.math.Aabb;
import net.elytrarace.voyager.api.math.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grid bounds and the cell classification, tested where they are observable.
 *
 * <p>Two of the three things this class reproduces cannot be seen through
 * {@link MinestomCollisionSpace#boxesIntersecting(Aabb)}'s output at all, so they are pinned here
 * instead:
 *
 * <ul>
 *   <li><b>The {@code 1.0E-7} tolerance</b> only changes a bound when the coordinate is within
 *       {@code 1e-7} of a block boundary, and the cell it then adds is a further whole block away
 *       from the region. Nothing in Minecraft's block registry has a collision shape that reaches
 *       a whole block out of its own cell — the largest overhang across all {@code 32366} block
 *       states is {@code 0.5} — so no world can make that cell contribute a box.</li>
 *   <li><b>The edge and corner classes</b> are equally unobservable; see
 *       {@link MinestomCollisionSpace#admits(int, net.minestom.server.instance.block.Block)}.</li>
 * </ul>
 *
 * <p>The {@code -1} / {@code +1} margin, by contrast, <em>is</em> observable, and is pinned
 * end-to-end in {@link MinestomCollisionSpaceTest} rather than only here.
 */
class BlockCursorTest {

    @ParameterizedTest(name = "lowerBound({0}) == {1}")
    @CsvSource({
            // Ordinary fractional coordinates: floor, then one block of margin. Both signs, because
            // Math.floor and an (int) cast agree above zero and differ below it.
            "2.3,   1",
            "-1.4, -3",
            "-0.4, -2",
            "0.0,  -2",
            // 0.0 is the first case the tolerance moves: floor(0.0 - 1e-7) is -1, not 0, so the
            // bound is -2 where an untolerant floor would give -1. Same for any integer coordinate.
            "3.0,   1",
            "-2.0, -4",
            // A coordinate a hair above an integer is not moved by the tolerance.
            "3.0000002, 2",
    })
    void lowerBoundFloorsWithATolerantMarginOfOneBlock(double value, int expected) {
        assertThat(BlockCursor.lowerBound(value)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "upperBound({0}) == {1}")
    @CsvSource({
            "3.1,   4",
            "0.7,   1",
            "-1.4, -1",
            "0.0,   1",
            // The tolerance moves a coordinate a hair *below* an integer up past it: floor(2.99999999
            // + 1e-7) is 3, not 2, so the bound is 4 where an untolerant floor would give 3.
            "2.99999999, 4",
            "-2.00000001, -1",
    })
    void upperBoundFloorsWithATolerantMarginOfOneBlock(double value, int expected) {
        assertThat(BlockCursor.upperBound(value)).isEqualTo(expected);
    }

    @Test
    void overMapsEachAxisToItsOwnBound() {
        // Deliberately different on all six faces, so a transposed or swapped axis cannot survive.
        Aabb region = new Aabb(new Vec3(-1.4, 2.3, -2.4), new Vec3(0.7, 3.1, 0.9));

        BlockCursor cursor = BlockCursor.over(region);

        assertThat(cursor.minX()).isEqualTo(-3);
        assertThat(cursor.minY()).isEqualTo(1);
        assertThat(cursor.minZ()).isEqualTo(-4);
        assertThat(cursor.maxX()).isEqualTo(1);
        assertThat(cursor.maxY()).isEqualTo(4);
        assertThat(cursor.maxZ()).isEqualTo(1);
    }

    @ParameterizedTest(name = "cellType({0}, {1}, {2}) == {3}")
    @CsvSource({
            // Grid below is x -3..1, y 1..4, z -4..1 — the same asymmetric grid as above, so a cell
            // that is extreme on one axis is interior on the others at a different coordinate.
            "-1, 2, -2, 0",   // interior on all three
            "-3, 2, -2, 1",   // x at its minimum
            "1,  2, -2, 1",   // x at its maximum
            "-1, 1, -2, 1",   // y at its minimum
            "-1, 4, -2, 1",   // y at its maximum
            "-1, 2, -4, 1",   // z at its minimum
            "-1, 2,  1, 1",   // z at its maximum
            "-3, 1, -2, 2",   // x and y
            "-3, 2, -4, 2",   // x and z
            "-1, 4,  1, 2",   // y and z
            "-3, 1, -4, 3",   // all three, the low corner
            "1,  4,  1, 3",   // all three, the high corner
            "-3, 4,  1, 3",   // all three, mixed extremes
    })
    void cellTypeCountsTheAxesAtAGridExtreme(int x, int y, int z, int expected) {
        BlockCursor cursor = BlockCursor.over(new Aabb(new Vec3(-1.4, 2.3, -2.4), new Vec3(0.7, 3.1, 0.9)));

        assertThat(cursor.cellType(x, y, z)).isEqualTo(expected);
    }

    @Test
    void cellTypeCountsAnAxisOnceWhenItsMinimumAndMaximumCoincide() {
        // Cursor3D counts per axis with `x == 0 || x == width - 1`, a single increment even on a
        // one-cell axis. A grid this thin cannot arise from over(), whose margin makes every axis at
        // least three cells wide, but the transcription is of the counting rule, not of the grid.
        BlockCursor cursor = new BlockCursor(4, 1, -4, 4, 4, 1);

        assertThat(cursor.cellType(4, 2, -2)).isEqualTo(1);
    }
}
