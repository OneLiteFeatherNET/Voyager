package net.elytrarace.voyager.platform.collision;

import net.elytrarace.voyager.api.math.Aabb;

/**
 * The block grid Vanilla walks when it collects collision candidates for a region — bounds and cell
 * classification, transcribed from {@code BlockCollisions}' constructor and {@code Cursor3D}.
 *
 * <p>Both halves are load-bearing and neither can be borrowed from Minestom:
 *
 * <ul>
 *   <li><b>The bounds.</b> {@code floor(min - 1.0E-7) - 1} to {@code floor(max + 1.0E-7) + 1} per
 *       axis — a block of margin on every side, plus a tolerance applied before the floor.
 *       Minestom's {@code BoundingBox.PointIterator} resets to {@code floor(min)..floor(max)} with
 *       neither, so its candidate set is a different one.</li>
 *   <li><b>The cell class.</b> {@code Cursor3D.getNextType()} counts how many of the three axes sit
 *       on the grid's first or last index: {@code 0} inside, {@code 1} a face, {@code 2} an edge,
 *       {@code 3} a corner. {@code BlockCollisions.computeNext} then admits a corner never, an edge
 *       only for a moving piston, and a face only for a block with a large collision shape.</li>
 * </ul>
 *
 * <p>Because the margin is a whole block, the outermost layer of this grid never touches the region
 * itself: a cell at the lower extreme of an axis spans {@code [floor(min) - 1, floor(min)]} while
 * the region starts at {@code min >= floor(min)}. That is the whole reason Vanilla's filter is
 * shaped the way it is — only a shape that leaves its own cell can reach back in from out there.
 */
record BlockCursor(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** {@code Cursor3D.TYPE_INSIDE} — no axis at a grid extreme. */
    static final int TYPE_INSIDE = 0;

    /** {@code Cursor3D.TYPE_FACE} — exactly one axis at a grid extreme. */
    static final int TYPE_FACE = 1;

    /** {@code Cursor3D.TYPE_EDGE} — two axes at a grid extreme. */
    static final int TYPE_EDGE = 2;

    /** {@code Cursor3D.TYPE_CORNER} — all three axes at a grid extreme. */
    static final int TYPE_CORNER = 3;

    /**
     * The tolerance {@code BlockCollisions} applies before flooring, so a bound that sits a hair
     * under a block boundary still counts the block below it.
     */
    private static final double BOUNDS_TOLERANCE = 1.0e-7;

    static BlockCursor over(Aabb region) {
        return new BlockCursor(
                lowerBound(region.min().x()),
                lowerBound(region.min().y()),
                lowerBound(region.min().z()),
                upperBound(region.max().x()),
                upperBound(region.max().y()),
                upperBound(region.max().z()));
    }

    /** {@code Mth.floor(value - 1.0E-7) - 1}; {@code Mth.floor(double)} is {@code (int) Math.floor}. */
    static int lowerBound(double value) {
        return (int) Math.floor(value - BOUNDS_TOLERANCE) - 1;
    }

    /** {@code Mth.floor(value + 1.0E-7) + 1}. */
    static int upperBound(double value) {
        return (int) Math.floor(value + BOUNDS_TOLERANCE) + 1;
    }

    /**
     * {@code Cursor3D.getNextType()} for the cell at {@code (x, y, z)} — the number of axes on which
     * the cell sits at the grid's first or last index.
     */
    int cellType(int x, int y, int z) {
        int extremes = 0;
        if (x == minX || x == maxX) {
            extremes++;
        }
        if (y == minY || y == maxY) {
            extremes++;
        }
        if (z == minZ || z == maxZ) {
            extremes++;
        }
        return extremes;
    }
}
