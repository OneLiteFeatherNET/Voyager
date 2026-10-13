package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.race.line.RacingLine;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The racing line parameterised by arc length: a distance in blocks along the course maps to one point and
 * one unit tangent.
 *
 * <p>Built once per map and shared by every computer racer on it. The points are the line's points with
 * any repeated neighbour dropped, so every segment has positive length and every tangent is defined. Lookups
 * are a binary search over the cumulative lengths, which is the whole per-tick cost of finding where a racer
 * is on the course.
 */
@ApiStatus.Internal
public final class ComPath {

    /** Two neighbouring points closer than this are the same point for the purpose of a direction. */
    private static final double MINIMUM_SEGMENT = 1e-9;

    private final List<Vec3> points;

    /** {@code cumulative[i]} is the arc length from the first point to {@code points.get(i)}. */
    private final double[] cumulative;

    private final List<Ring> rings;

    /** {@code ringDistances[k]} is the arc length from the first point to the centre of ring {@code k}. */
    private final double[] ringDistances;

    private ComPath(List<Vec3> points, double[] cumulative, List<Ring> rings, double[] ringDistances) {
        this.points = points;
        this.cumulative = cumulative;
        this.rings = rings;
        this.ringDistances = ringDistances;
    }

    /**
     * Builds the arc-length path of one racing line, with the rings the line runs through.
     *
     * <p>The ring distances are measured on the line as it was given, before repeated neighbours are dropped.
     * A dropped neighbour has no length, so the distances are the same on either list.
     *
     * @param line the line a computer racer follows
     * @param rings the rings of the course, in ring order, one for each ring anchor of {@code line}
     * @return the path, shared by every racer on that map
     * @throws IllegalArgumentException if the line has fewer than two distinct points, since a direction is
     *     undefined without a segment, or if the ring count does not match the line's ring anchors
     */
    public static ComPath of(RacingLine line, List<Ring> rings) {
        return build(line.points(), line.ringPoints(), rings);
    }

    /**
     * Builds the path of a racing line that starts on the grid: the grid is the first point of the course, and the
     * line follows it, so distance zero is the grid and every ring sits at its distance from there.
     *
     * <p>The grid is the map's spawn, which is where a computer racer stands before the start. The line's own first
     * point is the first ring's centre, which is eight blocks above the spawn on the committed courses, so a path
     * that began at that point would put the racers on the grid at the height of the first ring.
     *
     * @param grid the point the racers stand on before the start, the map's spawn
     * @param line the racing line of the map
     * @param rings the rings of the course, in ring order, one for each ring anchor of {@code line}
     * @return the path, shared by every racer on that map
     * @throws IllegalArgumentException if the ring count does not match the line's ring anchors, or the grid and the
     *     line together have fewer than two distinct points
     */
    public static ComPath fromGrid(Vec3 grid, RacingLine line, List<Ring> rings) {
        List<Vec3> points = new ArrayList<>(line.points().size() + 1);
        points.add(grid);
        points.addAll(line.points());
        List<Integer> ringPoints = new ArrayList<>(line.ringPoints().size());
        for (int anchor : line.ringPoints()) {
            ringPoints.add(anchor + 1);
        }
        return build(points, ringPoints, rings);
    }

    /** The one builder behind both factories: the points, the ring anchors, and the rings the anchors name. */
    private static ComPath build(List<Vec3> points, List<Integer> ringPoints, List<Ring> rings) {
        if (rings.size() != ringPoints.size()) {
            throw new IllegalArgumentException(
                    "a computer racer's path needs one ring for each of the line's %s ring anchors, was %s"
                            .formatted(ringPoints.size(), rings.size()));
        }
        double[] originalDistance = new double[points.size()];
        for (int i = 1; i < originalDistance.length; i++) {
            originalDistance[i] = originalDistance[i - 1] + points.get(i - 1).distanceTo(points.get(i));
        }
        double[] ringDistances = new double[rings.size()];
        for (int ring = 0; ring < ringDistances.length; ring++) {
            ringDistances[ring] = originalDistance[ringPoints.get(ring)];
        }
        List<Vec3> distinct = new ArrayList<>(points.size());
        for (Vec3 point : points) {
            if (distinct.isEmpty() || point.distanceTo(distinct.getLast()) > MINIMUM_SEGMENT) {
                distinct.add(point);
            }
        }
        if (distinct.size() < 2) {
            throw new IllegalArgumentException(
                    "a computer racer's path needs at least two distinct points, was %s"
                            .formatted(distinct.size()));
        }
        double[] cumulative = new double[distinct.size()];
        for (int i = 1; i < distinct.size(); i++) {
            cumulative[i] = cumulative[i - 1] + distinct.get(i - 1).distanceTo(distinct.get(i));
        }
        return new ComPath(List.copyOf(distinct), cumulative, List.copyOf(rings), ringDistances);
    }

    /** The total arc length of the course, in blocks. */
    public double length() {
        return cumulative[cumulative.length - 1];
    }

    /** The rings of the course, in ring order. */
    public List<Ring> rings() {
        return rings;
    }

    /**
     * One ring of the course.
     *
     * @param index the ring, counted from zero
     * @return the ring
     */
    public Ring ring(int index) {
        return rings.get(index);
    }

    /** How many rings the course runs through. */
    public int ringCount() {
        return rings.size();
    }

    /**
     * The arc length from the first point to the centre of a ring, in blocks.
     *
     * @param index the ring, counted from zero
     * @return the distance along the course at which that ring sits
     */
    public double ringDistance(int index) {
        return ringDistances[index];
    }

    /**
     * The point {@code distance} blocks along the course. A distance before the start is the first point and
     * one past the end is the last.
     *
     * @param distance blocks from the first point
     * @return the point on the course
     */
    public Vec3 pointAt(double distance) {
        if (distance <= 0.0) {
            return points.getFirst();
        }
        if (distance >= length()) {
            return points.getLast();
        }
        int segment = segmentAt(distance);
        double start = cumulative[segment];
        double end = cumulative[segment + 1];
        double t = (distance - start) / (end - start);
        return points.get(segment).scale(1.0 - t).plus(points.get(segment + 1).scale(t));
    }

    /**
     * The unit direction of the course at {@code distance} blocks along it. A distance outside the course takes
     * the tangent of the first or last segment.
     *
     * @param distance blocks from the first point
     * @return a vector of length one, pointing forwards along the course
     */
    public Vec3 tangentAt(double distance) {
        int segment = segmentAt(distance);
        Vec3 direction = points.get(segment + 1).minus(points.get(segment));
        return direction.scale(1.0 / direction.length());
    }

    /** The segment that contains {@code distance}, clamped to the first and last segments. */
    private int segmentAt(double distance) {
        int last = cumulative.length - 2;
        int index = Arrays.binarySearch(cumulative, distance);
        int segment = index >= 0 ? index : -index - 2;
        return Math.max(0, Math.min(last, segment));
    }
}
