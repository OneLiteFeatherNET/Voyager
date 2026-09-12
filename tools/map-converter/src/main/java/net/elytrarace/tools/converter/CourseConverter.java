package net.elytrarace.tools.converter;

import net.elytrarace.tools.converter.exception.InvalidCourseException;
import net.elytrarace.tools.converter.legacy.LegacyLocation;
import net.elytrarace.tools.converter.legacy.LegacyPortal;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the old {@code portals.json} into the rings a {@code MapDefinition} is made of.
 *
 * <p>Three things happen here that the old format cannot express, and each is a decision rather than
 * a transcription.
 *
 * <p><strong>Renumbering.</strong> The recorded indices run from one; {@code MapDefinition} requires
 * {@code 0..n-1} in list order, because a progress tracker reads the next ring by
 * {@code rings.get(passedCount)}. The renumbering is a subtraction, but the check around it is the
 * point: the indices must already form a contiguous ascending run in file order. A course whose
 * indices skip a number has lost a ring, and re-indexing by file order would hide that by producing
 * a shorter course that still looks complete — the same shape of defect as a ring whose normal was
 * guessed, one level up.
 *
 * <p><strong>The normal's sign.</strong> {@link RingGeometry#axis} gives the plane but not the
 * direction, and the direction decides which way through the ring scores. The sign is taken from the
 * <em>flow</em> at that ring: normalise the direction the player arrives from and the direction they
 * leave towards, add the two, and flip the axis to agree with that sum. It is the bisector of the
 * flight path, which is what "the way you are travelling as you pass through" means.
 *
 * <p>Three simpler rules were measured against the 35 rings of {@code ElytraraceBlueAndRed} and
 * rejected, scored by the smallest {@code |dot(normal, direction)|} over the course — a value near
 * zero means the direction is almost parallel to the ring's plane and the sign is a coin flip:
 *
 * <pre>
 * outgoing   c[i+1] - c[i]                    min 0.050   ambiguous at rings 18 and 27
 * incoming   c[i]   - c[i-1]                  min 0.057   ambiguous at rings 3, 6, 7, 8 and 26
 * tangent    c[i+1] - c[i-1]                  min 0.243   ambiguous at ring 27
 * flow       unit(incoming) + unit(outgoing)  min 0.502   nothing ambiguous
 * </pre>
 *
 * <p>Flow is the only one of the four with no ambiguous ring and it beats the runner-up by a factor
 * of two. The first ring has no incoming direction and the last no outgoing one; each uses the one
 * it has.
 *
 * <p><strong>The margin is asserted, not assumed.</strong> {@link #MINIMUM_FLOW_MARGIN} is checked
 * per ring and a course below it is rejected by ring index. The measured worst case is 0.502, so the
 * threshold leaves room for a differently shaped course while still refusing one where the rule
 * degenerates. Writing a normal whose sign was decided by a dot product of 0.05 is writing a coin
 * flip into committed data, where it is indistinguishable from a measurement.
 */
public final class CourseConverter {

    /**
     * How decisively the flight path must pass through a ring for its normal's sign to be believed.
     * Below this, the flow bisector is close enough to the ring's plane that rounding could pick
     * either direction.
     */
    public static final double MINIMUM_FLOW_MARGIN = 0.35;

    private CourseConverter() {
    }

    /**
     * Converts a whole course.
     *
     * @param portals the entries of one {@code portals.json}, in file order
     * @param points  the score a ring awards, seeded uniformly because the old data records no
     *                per-ring variation
     * @return the rings, indexed {@code 0..n-1} in the same order
     * @throws InvalidCourseException if the indices are not a contiguous ascending run, if a ring's
     *                                geometry cannot be read, or if any ring's normal would have an
     *                                undecided sign
     */
    public static List<Ring> toRings(List<LegacyPortal> portals, int points) {
        if (portals.size() < 2) {
            throw new InvalidCourseException(
                    ("a course needs at least two rings so each ring has a flight direction to orient "
                            + "its normal by, got %s").formatted(portals.size()));
        }
        requireContiguousIndices(portals);

        List<Vec3> centers = new ArrayList<>(portals.size());
        List<Vec3> axes = new ArrayList<>(portals.size());
        List<Double> radii = new ArrayList<>(portals.size());
        for (LegacyPortal portal : portals) {
            Vec3 center = centerOf(portal);
            List<Vec3> rim = rimOf(portal);
            centers.add(center);
            axes.add(RingGeometry.axis(center, rim));
            radii.add(RingGeometry.radius(center, rim));
        }

        List<Ring> rings = new ArrayList<>(portals.size());
        for (int i = 0; i < portals.size(); i++) {
            LegacyPortal portal = portals.get(i);
            Vec3 flow = flowAt(centers, i, portal.index());
            double alignment = axes.get(i).dot(flow);
            if (Math.abs(alignment) < MINIMUM_FLOW_MARGIN) {
                throw new InvalidCourseException(
                        ("ring %s lies too nearly along the flight path to decide which way through it "
                                + "counts: |dot(normal %s, flow %s)| is %s, below the %s this "
                                + "conversion will act on").formatted(
                                portal.index(), axes.get(i), flow, Math.abs(alignment), MINIMUM_FLOW_MARGIN));
            }
            rings.add(new Ring(i, centers.get(i), orient(axes.get(i), alignment), radii.get(i), points,
                    typeOf(portal)));
        }
        return List.copyOf(rings);
    }

    /**
     * Points the axis the way the course is flown, without leaving a negative zero behind.
     *
     * <p>The sign flip is the whole point of this method; the zero handling is not cosmetic. An
     * axis-aligned ring's normal has two zero components, and negating one of them produces
     * {@code -0.0}, which is numerically equal to {@code 0.0} but which {@code Double.compare} — and
     * therefore record equality, and therefore every assertion written against a {@code Vec3} —
     * treats as a different value. Committing that would make a test about which way a ring faces
     * fail on which way a sign bit fell.
     */
    private static Vec3 orient(Vec3 axis, double alignment) {
        double sign = alignment >= 0.0 ? 1.0 : -1.0;
        return new Vec3(positiveZero(axis.x() * sign), positiveZero(axis.y() * sign), positiveZero(axis.z() * sign));
    }

    private static double positiveZero(double value) {
        // -0.0 == 0.0 is true, so this replaces a negative zero and leaves everything else alone.
        return value == 0.0 ? 0.0 : value;
    }

    /**
     * The bisector of the flight path at ring {@code i}, unit length.
     *
     * <p>Incoming and outgoing are normalised <em>before</em> being added, so a long approach and a
     * short exit weigh the same. Adding the raw differences instead would let whichever leg happens
     * to be longer decide the sign on its own, which is the {@code outgoing} and {@code incoming}
     * rules the table in the class javadoc rejects, reached by accident.
     */
    private static Vec3 flowAt(List<Vec3> centers, int position, Integer recordedIndex) {
        @Nullable Vec3 incoming = position > 0
                ? unit(centers.get(position - 1), centers.get(position), recordedIndex)
                : null;
        @Nullable Vec3 outgoing = position < centers.size() - 1
                ? unit(centers.get(position), centers.get(position + 1), recordedIndex)
                : null;
        if (incoming == null) {
            return outgoing;
        }
        if (outgoing == null) {
            return incoming;
        }
        Vec3 sum = incoming.plus(outgoing);
        if (sum.length() <= 1e-9) {
            throw new InvalidCourseException(
                    ("the course doubles back exactly on itself at ring %s — the approach and the exit "
                            + "are opposite, so the flight path gives its normal no direction")
                            .formatted(recordedIndex));
        }
        return sum.scale(1.0 / sum.length());
    }

    private static Vec3 unit(Vec3 from, Vec3 to, Integer recordedIndex) {
        Vec3 difference = to.minus(from);
        if (difference.length() <= 1e-9) {
            throw new InvalidCourseException(
                    ("two consecutive rings share the centre %s at ring %s, so there is no flight "
                            + "direction between them").formatted(from, recordedIndex));
        }
        return difference.scale(1.0 / difference.length());
    }

    private static void requireContiguousIndices(List<LegacyPortal> portals) {
        Integer first = portals.getFirst().index();
        if (first == null) {
            throw new InvalidCourseException("the first portal has no index");
        }
        for (int i = 0; i < portals.size(); i++) {
            Integer actual = portals.get(i).index();
            int expected = first + i;
            if (actual == null || actual != expected) {
                throw new InvalidCourseException(
                        ("portal indices must be a contiguous ascending run in file order; position %s "
                                + "holds index %s where %s was expected, so a ring is missing or the "
                                + "file is out of order").formatted(i, actual, expected));
            }
        }
    }

    private static Vec3 centerOf(LegacyPortal portal) {
        List<LegacyLocation> centers = locationsOf(portal).stream().filter(LegacyLocation::isCenter).toList();
        if (centers.size() != 1) {
            throw new InvalidCourseException(
                    ("ring %s must record exactly one location with center=true, found %s")
                            .formatted(portal.index(), centers.size()));
        }
        return toVec3(centers.getFirst(), portal);
    }

    private static List<Vec3> rimOf(LegacyPortal portal) {
        return locationsOf(portal).stream()
                .filter(location -> !location.isCenter())
                .map(location -> toVec3(location, portal))
                .toList();
    }

    private static List<LegacyLocation> locationsOf(LegacyPortal portal) {
        List<LegacyLocation> locations = portal.locations();
        if (locations == null || locations.isEmpty()) {
            throw new InvalidCourseException("ring %s records no locations at all".formatted(portal.index()));
        }
        return locations;
    }

    private static Vec3 toVec3(LegacyLocation location, LegacyPortal portal) {
        if (location.x() == null || location.y() == null || location.z() == null) {
            throw new InvalidCourseException(
                    ("ring %s has a location missing a coordinate: x=%s, y=%s, z=%s")
                            .formatted(portal.index(), location.x(), location.y(), location.z()));
        }
        return new Vec3(location.x(), location.y(), location.z());
    }

    /**
     * The ring's type, refusing anything it does not recognise.
     *
     * <p>The old loader fell back to {@code STANDARD} on an unknown or missing name and logged a
     * warning nobody read. Here it stops: a {@code BOOST} ring silently demoted to {@code STANDARD}
     * changes how the course plays, and a one-shot conversion has exactly one chance to notice.
     */
    private static RingType typeOf(LegacyPortal portal) {
        return RingType.byName(portal.type()).orElseThrow(() -> new InvalidCourseException(
                "ring %s has the unrecognised type '%s'".formatted(portal.index(), portal.type())));
    }
}
