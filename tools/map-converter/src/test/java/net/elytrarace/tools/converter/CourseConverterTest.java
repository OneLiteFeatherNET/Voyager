package net.elytrarace.tools.converter;

import net.elytrarace.tools.converter.exception.InvalidCourseException;
import net.elytrarace.tools.converter.legacy.LegacyGuide;
import net.elytrarace.tools.converter.legacy.LegacyLocation;
import net.elytrarace.tools.converter.legacy.LegacyPortal;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseConverterTest {

    private static final Offset<Double> TIGHT = Offset.offset(1e-12);

    // Three rings that differ in every column a bug could collapse: a different centre, a different
    // radius, a different ring type, a different plane, and — because the recorded indices are
    // one-based — a recorded index that never equals the position in the list. Ring B's plane is
    // tilted, so its normal has no zero component to hide a wrong one in.
    private static final int[] CENTER_A = {85, -54, 54};
    private static final int[] CENTER_B = {2, -31, 69};
    private static final int[] CENTER_C = {-86, -15, 64};

    private static final int[] EAST = {1, 0, 0};
    private static final int[] UP = {0, 1, 0};
    private static final int[] SOUTH = {0, 0, 1};

    private static final Vec3 TILTED_NORMAL = new Vec3(-2.0 / 3.0, 2.0 / 3.0, -1.0 / 3.0);

    private static List<LegacyPortal> course(int firstIndex) {
        return List.of(
                LegacyPortals.ring(firstIndex, CENTER_A, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(firstIndex + 1, CENTER_B, "BOOST",
                        LegacyPortals.oppositeFirst(new int[] {1, 2, 2}, new int[] {2, 1, -2})),
                LegacyPortals.ring(firstIndex + 2, CENTER_C, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 1, 4)));
    }

    @Test
    void renumbersOneBasedIndicesToThePositionTheProgressTrackerReadsBy() {
        List<Ring> rings = CourseConverter.toRings(course(1), 7);

        assertThat(rings).extracting(Ring::index).containsExactly(0, 1, 2);
        assertThat(rings).extracting(Ring::center).containsExactly(
                new Vec3(85, -54, 54), new Vec3(2, -31, 69), new Vec3(-86, -15, 64));
        assertThat(rings).extracting(Ring::type)
                .containsExactly(RingType.STANDARD, RingType.BOOST, RingType.STANDARD);
        // The seed is a parameter, not a constant in the code: 7 here, never the 10 the conversion
        // is actually run with, so a hard-coded default cannot pass.
        assertThat(rings).extracting(Ring::points).containsExactly(7, 7, 7);
        assertThat(rings.get(0).radius()).isCloseTo(Math.sqrt(13), TIGHT);
        assertThat(rings.get(1).radius()).isCloseTo(3.0, TIGHT);
        assertThat(rings.get(2).radius()).isCloseTo(Math.sqrt(17), TIGHT);
    }

    @Test
    void acceptsAContiguousRunThatDoesNotStartAtOne() {
        // The renumbering subtracts the first recorded index rather than assuming it is 1, and the
        // output index is the position either way. A course recorded from 5 proves the two are not
        // the same number.
        List<Ring> rings = CourseConverter.toRings(course(5), 7);

        assertThat(rings).extracting(Ring::index).containsExactly(0, 1, 2);
    }

    @Test
    void orientsEveryNormalAlongTheDirectionOfFlight() {
        List<Ring> rings = CourseConverter.toRings(course(1), 7);

        // The first ring has only an outgoing leg and the last only an incoming one; the middle ring
        // is oriented by the bisector of both. All three point the way the course is flown — west.
        assertNormal(rings.get(0), new Vec3(-1, 0, 0));
        assertNormal(rings.get(1), TILTED_NORMAL);
        assertNormal(rings.get(2), new Vec3(-1, 0, 0));
    }

    @Test
    void flipsEveryNormalWhenTheSameRingsAreFlownTheOtherWay() {
        List<LegacyPortal> reversed = new ArrayList<>(course(1));
        Collections.reverse(reversed);
        // Reversing the list reverses the rings' order but not their recorded indices, which must
        // stay a contiguous ascending run; rewrite them so the test measures orientation and not
        // index validation.
        List<LegacyPortal> renumbered = new ArrayList<>();
        for (int i = 0; i < reversed.size(); i++) {
            renumbered.add(new LegacyPortal(i + 1, reversed.get(i).locations(), reversed.get(i).type()));
        }

        List<Ring> rings = CourseConverter.toRings(renumbered, 7);

        assertNormal(rings.get(0), new Vec3(1, 0, 0));
        assertNormal(rings.get(1), TILTED_NORMAL.scale(-1));
        assertNormal(rings.get(2), new Vec3(1, 0, 0));
    }

    @Test
    void weighsTheApproachAndTheExitEquallyRatherThanByLength() {
        // A 100-block approach that descends 5, then a 2-block climb over 1 block of ground. Adding
        // the raw differences would let the long leg carry the sign and orient the middle ring
        // downwards; normalising each leg first — which is what the flow rule says — orients it
        // upwards. The two answers are opposite, so this is the one fixture that tells them apart.
        List<LegacyPortal> portals = List.of(
                LegacyPortals.ring(1, new int[] {-100, 0, 5}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(2, new int[] {0, 0, 0}, "STANDARD", LegacyPortals.octagon(EAST, UP, 2, 3)),
                LegacyPortals.ring(3, new int[] {1, 0, 2}, "STANDARD", LegacyPortals.octagon(EAST, UP, 2, 3)));

        List<Ring> rings = CourseConverter.toRings(portals, 7);

        assertNormal(rings.get(1), new Vec3(0, 0, 1));
    }

    @Test
    void leavesNoNegativeZeroInANormalItHadToFlip() {
        // Flipping an axis-aligned normal negates its two zero components into -0.0, which is
        // numerically equal to 0.0 and unequal to it under record equality. Asserted with equals
        // rather than a tolerance, because a tolerance is exactly what cannot see the difference.
        List<Ring> rings = CourseConverter.toRings(course(1), 7);

        assertThat(rings.get(0).normal()).isEqualTo(new Vec3(-1, 0, 0));
        assertThat(rings.get(2).normal()).isEqualTo(new Vec3(-1, 0, 0));
    }

    @Test
    void rejectsACourseWhoseIndicesSkipANumber() {
        List<LegacyPortal> portals = new ArrayList<>(course(1));
        portals.set(2, new LegacyPortal(4, portals.get(2).locations(), portals.get(2).type()));

        assertThatThrownBy(() -> CourseConverter.toRings(portals, 7))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("position 2 holds index 4 where 3 was expected");
    }

    @Test
    void rejectsACourseWhoseIndicesAreNotAscendingInFileOrder() {
        List<LegacyPortal> portals = new ArrayList<>(course(1));
        Collections.swap(portals, 0, 1);

        assertThatThrownBy(() -> CourseConverter.toRings(portals, 7))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("contiguous ascending run");
    }

    @Test
    void rejectsARingTheFlightPathBarelyPassesThrough() {
        // The middle ring stands edge-on to the flight path: the bisector meets its normal at
        // |dot| = 0.0985, so which side counts as "through" is a coin flip. The measured worst case
        // on the shipped course is 0.502, five times this.
        List<LegacyPortal> portals = List.of(
                LegacyPortals.ring(1, new int[] {-10, 0, 0}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)),
                LegacyPortals.ring(2, new int[] {0, 0, 0}, "STANDARD", LegacyPortals.octagon(EAST, UP, 2, 3)),
                LegacyPortals.ring(3, new int[] {10, 0, 2}, "STANDARD", LegacyPortals.octagon(UP, SOUTH, 2, 3)));

        assertThatThrownBy(() -> CourseConverter.toRings(portals, 7))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("ring 2 lies too nearly along the flight path");
    }

    @Test
    void rejectsARingWithoutExactlyOneCentre() {
        List<LegacyPortal> portals = new ArrayList<>(course(1));
        List<LegacyLocation> locations = new ArrayList<>(portals.get(1).locations());
        locations.add(new LegacyLocation(0, 0, 0, true));
        portals.set(1, new LegacyPortal(2, locations, "BOOST"));

        assertThatThrownBy(() -> CourseConverter.toRings(portals, 7))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("exactly one location with center=true, found 2");
    }

    @Test
    void rejectsAnUnrecognisedRingType() {
        List<LegacyPortal> portals = new ArrayList<>(course(1));
        portals.set(0, new LegacyPortal(1, portals.get(0).locations(), "TURBO"));

        assertThatThrownBy(() -> CourseConverter.toRings(portals, 7))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("unrecognised type 'TURBO'");
    }

    @Test
    void rejectsACourseWithNoDirectionToOrientBy() {
        assertThatThrownBy(() -> CourseConverter.toRings(course(1).subList(0, 1), 7))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("at least two rings");
    }

    @Test
    void rejectsTwoConsecutiveRingsSharingACentre() {
        List<LegacyPortal> portals = new ArrayList<>(course(1));
        portals.set(1, LegacyPortals.ring(2, CENTER_A, "BOOST",
                LegacyPortals.oppositeFirst(new int[] {1, 2, 2}, new int[] {2, 1, -2})));

        assertThatThrownBy(() -> CourseConverter.toRings(portals, 7))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("share the centre");
    }

    private static void assertNormal(Ring ring, Vec3 expected) {
        assertThat(ring.normal().x()).as("normal x of ring %s", ring.index()).isCloseTo(expected.x(), TIGHT);
        assertThat(ring.normal().y()).as("normal y of ring %s", ring.index()).isCloseTo(expected.y(), TIGHT);
        assertThat(ring.normal().z()).as("normal z of ring %s", ring.index()).isCloseTo(expected.z(), TIGHT);
    }
    // ----------------------------------------------------------------------------------------
    // Guide points
    // ----------------------------------------------------------------------------------------

    /**
     * The guide's order index is carried across unchanged when the recorded portals start at 1, which
     * is what the real file does — ring n at (n - 1) * 100 in the old numbering is ring n - 1 at
     * (n - 1) * 100 in the new one.
     *
     * <p>Two guides in one gap, and the second one first in file order: the conversion preserves both
     * and does not reorder them, because ordering the line is the map's job and dropping one would be
     * the silent failure the committed course's own 2450/2475 pair is waiting to catch.
     */
    @Test
    void carriesAGuidesOrderIndexAcrossUnchangedWhenTheRecordedRingsStartAtOne() {
        List<GuidePoint> points = CourseConverter.toGuidePoints(
                List.of(guide(175, 20.5, 76.25, 80.75), guide(150, -79.36, -17.16, 76.31)), course(1));

        assertThat(points).extracting(GuidePoint::orderIndex).containsExactly(175, 150);
        assertThat(points).extracting(GuidePoint::position).containsExactly(
                new Vec3(20.5, 76.25, 80.75), new Vec3(-79.36, -17.16, 76.31));
    }

    /**
     * A course whose recorded portals do not start at 1 has its guides shifted by the same amount its
     * rings were renumbered by.
     *
     * <p>The real file starts at 1 and this is an identity there, which is exactly why it is asserted
     * on a file that does not: "the guides happen to line up" is a coincidence that stops being true
     * without anything saying so. Portals starting at 5 means ring 5 became ring 0, so a guide written
     * at 450 — between recorded rings 5 and 6 — is a guide at 50, between rings 0 and 1.
     */
    @Test
    void shiftsGuidesByTheSameRenumberingTheRingsGot() {
        List<GuidePoint> points = CourseConverter.toGuidePoints(List.of(guide(450, 1.0, 2.0, 3.0)), course(5));

        assertThat(points).extracting(GuidePoint::orderIndex).containsExactly(50);
        assertThat(points.getFirst().afterRing()).isZero();
    }

    @Test
    void convertsACourseWithNoGuideFileAtAllIntoNoGuidePoints() {
        assertThat(CourseConverter.toGuidePoints(List.of(), course(1))).isEmpty();
    }

    /**
     * A guide that does not land between two rings of this course stops the conversion.
     *
     * <p>The three-ring fixture spans order indices 0 to 200, so 250 is past its last ring. That is
     * what a guide file written against a different, longer version of the course looks like, and
     * carrying it across would put a control point beyond the finish where nothing would ever draw it
     * — or, one gap earlier, bend the line somewhere nobody chose.
     */
    @Test
    void refusesAGuideThatDoesNotLandBetweenTwoOfThisCoursesRings() {
        assertThatThrownBy(() -> CourseConverter.toGuidePoints(List.of(guide(250, 1.0, 2.0, 3.0)), course(1)))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("rings 2 and 3");
        assertThatThrownBy(() -> CourseConverter.toGuidePoints(List.of(guide(-50, 1.0, 2.0, 3.0)), course(1)))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("rings -1 and 0");
    }

    @Test
    void refusesAGuideMissingACoordinateOrItsOrderIndex() {
        // Boxed on purpose: a missing y and a y of zero are different things, and the old files are
        // full of coordinates that are legitimately zero.
        assertThatThrownBy(() -> CourseConverter.toGuidePoints(
                List.of(new LegacyGuide(150, 1.0, null, 3.0)), course(1)))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() -> CourseConverter.toGuidePoints(
                List.of(new LegacyGuide(null, 1.0, 2.0, 3.0)), course(1)))
                .isInstanceOf(InvalidCourseException.class)
                .hasMessageContaining("incomplete");
    }

    private static LegacyGuide guide(int orderIndex, double x, double y, double z) {
        return new LegacyGuide(orderIndex, x, y, z);
    }

}
