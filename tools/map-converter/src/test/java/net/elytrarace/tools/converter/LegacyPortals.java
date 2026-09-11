package net.elytrarace.tools.converter;

import net.elytrarace.tools.converter.legacy.LegacyLocation;
import net.elytrarace.tools.converter.legacy.LegacyPortal;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds legacy portals for the tests, the way the setup wizard wrote them.
 *
 * <p>Two things about the shape of these fixtures are load-bearing rather than convenient.
 *
 * <p><strong>The centre is written last.</strong> That is where the real files put it, and a reader
 * that took {@code locations.getFirst()} as the centre — which is what the loader in the tree being
 * replaced fell back to — would otherwise never be caught.
 *
 * <p><strong>Rim offsets are given explicitly and in order around the ring.</strong> Generating them
 * from an angle would need rounding to the integer coordinates the old format stores, and rounded
 * points are no longer all the same distance from the centre. Worse, an ordering chosen for
 * convenience would hide the trap the real data holds: entry 0 and entry 1 of some rings are
 * diametrically opposite, so their cross product is zero and a derivation that only looks at the
 * first two pairs of points silently produces a default normal.
 */
final class LegacyPortals {

    private LegacyPortals() {
    }

    /**
     * A ring whose rim sits at {@code center + offset} for each offset given, in that order.
     *
     * @param index       the one-based index the wizard recorded
     * @param center      the ring's centre, in blocks
     * @param type        the ring type name, as the wizard spelled it
     * @param rimOffsets  the rim points relative to the centre, in the order the file lists them
     */
    static LegacyPortal ring(int index, int[] center, String type, int[]... rimOffsets) {
        List<LegacyLocation> locations = new ArrayList<>();
        for (int[] offset : rimOffsets) {
            locations.add(new LegacyLocation(
                    center[0] + offset[0], center[1] + offset[1], center[2] + offset[2], false));
        }
        locations.add(new LegacyLocation(center[0], center[1], center[2], true));
        return new LegacyPortal(index, locations, type);
    }

    /**
     * The eight rim offsets of an octagon in an axis-aligned plane, in order around the ring — the
     * exact shape every ring of {@code ElytraraceBlueAndRed} has, where {@code a} is 2 and {@code b}
     * is 3 and every point is {@code sqrt(13)} from the centre.
     *
     * <p>Entries 0 and 4 are opposite, entries 1 and 5, and so on; no two <em>adjacent</em> entries
     * are, which is why {@link #oppositeFirst} exists separately.
     *
     * @param first  a unit axis of the ring's plane
     * @param second the other unit axis of the ring's plane
     * @param a      the shorter coordinate of a vertex
     * @param b      the longer coordinate of a vertex
     */
    static int[][] octagon(int[] first, int[] second, int a, int b) {
        int[][] vertices = {{a, b}, {-a, b}, {-b, a}, {-b, -a}, {-a, -b}, {a, -b}, {b, -a}, {b, a}};
        int[][] offsets = new int[vertices.length][];
        for (int i = 0; i < vertices.length; i++) {
            offsets[i] = combine(first, vertices[i][0], second, vertices[i][1]);
        }
        return offsets;
    }

    /**
     * Four rim offsets whose first two are diametrically opposite, so the cross product of the first
     * pair is exactly the zero vector.
     *
     * @param first  one radius-length vector in the ring's plane
     * @param second the perpendicular radius-length vector in the same plane
     */
    static int[][] oppositeFirst(int[] first, int[] second) {
        return new int[][] {
                combine(first, 1, second, 0),
                combine(first, -1, second, 0),
                combine(first, 0, second, 1),
                combine(first, 0, second, -1)};
    }

    private static int[] combine(int[] first, int firstScale, int[] second, int secondScale) {
        return new int[] {
                first[0] * firstScale + second[0] * secondScale,
                first[1] * firstScale + second[1] * secondScale,
                first[2] * firstScale + second[2] * secondScale};
    }
}
