package net.elytrarace.voyager.race.line;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;

import java.util.ArrayList;
import java.util.List;

/**
 * One course's racing line: the curve through every ring centre, bent by the guide points between
 * them, sampled into the points a renderer draws.
 *
 * <p>The line is domain rather than presentation, which is why it is here and not beside the
 * particles. A racing line is a statement about the course — this is the way through it — and it has
 * to be computable in a JUnit test with no server anywhere, the same way a whole race is. What
 * {@code voyager-platform} adds is the decision about how much of it one racer sees and what it is
 * drawn with.
 *
 * <h2>The interleaving, which is the one rule that can be wrong quietly</h2>
 *
 * <p>Rings and guides share one order axis: ring {@code i} sits at {@code i *
 * }{@link GuidePoint#RING_ORDER_STRIDE} and a guide at whatever index falls between the two rings it
 * bends the line between. Building the control points is therefore a merge of two already-ordered
 * lists, and the two ways to get it wrong are both silent. Dropping the second guide in a gap — the
 * committed course has two between rings 24 and 25 — puts the line back through the terrain that
 * second guide was placed to avoid. Inserting a guide in the wrong gap sends the line across the map
 * and back. Neither throws; both are measurable, because a line that visits its points in the wrong
 * order is much longer than the rings alone, which is what {@code RacingLineTest} asserts on.
 *
 * @param points the sampled line, in flight order, from the first ring's centre to the last
 * @param ringPoints index into {@code points} of each ring's centre, in ring order
 */
public record RacingLine(List<Vec3> points, List<Integer> ringPoints) {

    public RacingLine {
        points = List.copyOf(points);
        ringPoints = List.copyOf(ringPoints);
        if (ringPoints.isEmpty()) {
            throw new IllegalArgumentException("a racing line runs through at least one ring");
        }
        if (ringPoints.getFirst() != 0 || ringPoints.getLast() != points.size() - 1) {
            throw new IllegalArgumentException(
                    ("a racing line starts at its first ring and ends at its last, but ring anchors %s "
                            + "do not span the %s sampled point(s)").formatted(ringPoints, points.size()));
        }
        for (int i = 1; i < ringPoints.size(); i++) {
            if (ringPoints.get(i) <= ringPoints.get(i - 1)) {
                throw new IllegalArgumentException(
                        "ring anchors must ascend along the line, were %s".formatted(ringPoints));
            }
        }
    }

    /**
     * The line through one course, sampled at that course's own particle spacing.
     *
     * <p>Not cheap, and not meant to be called per tick: it walks every segment twice, once to
     * estimate its length and once to sample it. The committed course comes out at about seventeen
     * hundred points. Compute it once per map and keep it — {@code GuideLineRenderer} does.
     *
     * @param map the course, whose rings, guide points and particle spacing this reads
     * @return the sampled line
     */
    public static RacingLine of(MapDefinition map) {
        List<Anchor> anchors = anchorsOf(map);
        List<Vec3> controlPoints = new ArrayList<>(anchors.size());
        List<Integer> ringPositions = new ArrayList<>(map.rings().size());
        for (Anchor anchor : anchors) {
            if (anchor.ring) {
                ringPositions.add(controlPoints.size());
            }
            controlPoints.add(anchor.position);
        }

        CatmullRom.Curve curve = CatmullRom.sample(controlPoints, map.guideLine().particleSpacing());
        List<Integer> ringPoints = new ArrayList<>(ringPositions.size());
        for (int position : ringPositions) {
            ringPoints.add(curve.controlPointIndices().get(position));
        }
        return new RacingLine(curve.points(), ringPoints);
    }

    /**
     * The stretch of line from one ring to another, both included.
     *
     * <p>Which rings is the caller's decision and deliberately not this class's: how much of the line
     * a racer should see is a question about the player, and the answer differs between a racer, a
     * spectator and a builder previewing a course. This answers only "where does that stretch run".
     *
     * @param firstRing the ring the stretch starts at
     * @param lastRing the ring it ends at; equal to {@code firstRing} for a stretch of one point
     * @return the sampled points between them, in flight order
     * @throws IndexOutOfBoundsException if either index is not a ring of this line
     * @throws IllegalArgumentException if {@code lastRing} lies before {@code firstRing}
     */
    public List<Vec3> between(int firstRing, int lastRing) {
        if (lastRing < firstRing) {
            throw new IllegalArgumentException(
                    "a stretch runs forwards along the line, was ring %s to ring %s"
                            .formatted(firstRing, lastRing));
        }
        return points.subList(ringPoints.get(firstRing), ringPoints.get(lastRing) + 1);
    }

    /** How many rings this line runs through. */
    public int ringCount() {
        return ringPoints.size();
    }

    /**
     * The rings and the guide points on one order axis, sorted into the order the line visits them.
     *
     * <p>A merge rather than a sort would do — both inputs are ordered, {@code MapDefinition}
     * guarantees the rings and {@code GuideLine} the guides — but the sort is three lines and cannot
     * be got subtly wrong, and it runs once per map rather than per tick. Ties cannot happen: a guide
     * point refuses an order index on a ring's own slot, and two guides refuse to share one.
     */
    private static List<Anchor> anchorsOf(MapDefinition map) {
        List<Anchor> anchors = new ArrayList<>(map.rings().size() + map.guideLine().points().size());
        for (Ring ring : map.rings()) {
            anchors.add(new Anchor(ring.index() * GuidePoint.RING_ORDER_STRIDE, ring.center(), true));
        }
        for (GuidePoint guide : map.guideLine().points()) {
            anchors.add(new Anchor(guide.orderIndex(), guide.position(), false));
        }
        anchors.sort((left, right) -> Integer.compare(left.orderIndex, right.orderIndex));
        return anchors;
    }

    /** One point the line passes through, and whether a ring is scored there. */
    private record Anchor(int orderIndex, Vec3 position, boolean ring) {
    }
}
