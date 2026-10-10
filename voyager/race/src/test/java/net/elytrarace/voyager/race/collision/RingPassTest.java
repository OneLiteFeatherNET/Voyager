package net.elytrarace.voyager.race.collision;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingPassTest {

    /** Axis-aligned, facing +z, centred on the origin's column. */
    private static final Ring FLAT =
            new Ring(0, new Vec3(0.0, 64.0, 100.0), new Vec3(0.0, 0.0, 1.0), 5.0, 10, RingType.STANDARD);

    /**
     * Tilted in the xy plane and standing in negative z. Every fixture above it faces +z, where two
     * of the three components of every dot product vanish; this one keeps all three alive, which is
     * the only way a mistake in the x or y term can show up at all.
     */
    private static final Vec3 TILTED_NORMAL = new Vec3(0.6, 0.8, 0.0);
    private static final Ring TILTED =
            new Ring(3, new Vec3(10.0, 70.0, -20.0), TILTED_NORMAL, 4.0, 25, RingType.CHECKPOINT);

    @Test
    void countsAPassStraightThroughTheCentre() {
        assertThat(RingPass.crosses(new Vec3(0.0, 64.0, 99.0), new Vec3(0.0, 64.0, 101.0), FLAT)).isTrue();
    }

    @Test
    void countsAPassExactlyOnTheRim() {
        // The rim is inclusive, matching the old tree. distance == radius counts.
        assertThat(RingPass.crosses(new Vec3(5.0, 64.0, 99.0), new Vec3(5.0, 64.0, 101.0), FLAT)).isTrue();
    }

    @Test
    void rejectsAPassJustOutsideTheRim() {
        assertThat(RingPass.crosses(new Vec3(5.001, 64.0, 99.0), new Vec3(5.001, 64.0, 101.0), FLAT)).isFalse();
    }

    @Test
    void rejectsMovementParallelToTheRingPlane() {
        assertThat(RingPass.crosses(new Vec3(0.0, 64.0, 99.0), new Vec3(10.0, 64.0, 99.0), FLAT)).isFalse();
    }

    @Test
    void rejectsAPlaneTheSegmentStopsShortOf() {
        // The plane is at z=100; the segment ends at z=95, so the crossing would need t = 2.
        assertThat(RingPass.crosses(new Vec3(0.0, 64.0, 90.0), new Vec3(0.0, 64.0, 95.0), FLAT)).isFalse();
    }

    @Test
    void rejectsAPlaneAlreadyBehindTheSegment() {
        // Starting past the plane and moving away needs t = -0.25.
        assertThat(RingPass.crosses(new Vec3(0.0, 64.0, 101.0), new Vec3(0.0, 64.0, 105.0), FLAT)).isFalse();
    }

    @Test
    void countsABackwardsPass() {
        // Direction is deliberately not checked, matching the old tree: flying back through a ring
        // counts as passing it. Whether that should change is a gameplay decision, not this test's.
        assertThat(RingPass.crosses(new Vec3(0.0, 64.0, 101.0), new Vec3(0.0, 64.0, 99.0), FLAT)).isTrue();
    }

    @Test
    void countsAPassThroughATiltedRingsCentre() {
        // from = centre - normal, to = centre + normal, so the crossing sits exactly at t = 0.5 and
        // the intersection is the centre itself. All three coordinates differ between from and to.
        Vec3 from = new Vec3(9.4, 69.2, -20.0);
        Vec3 to = new Vec3(10.6, 70.8, -20.0);

        assertThat(RingPass.crosses(from, to, TILTED)).isTrue();
    }

    @Test
    void countsAnOffCentreHitOnATiltedRing() {
        // Aimed three blocks along +z from the centre, which lies in the tilted ring's own plane
        // because the normal has no z component. 3 < radius 4, so it counts.
        assertThat(RingPass.crosses(new Vec3(9.4, 69.2, -17.0), new Vec3(10.6, 70.8, -17.0), TILTED)).isTrue();
    }

    @Test
    void rejectsAnOffCentreMissOnATiltedRing() {
        // Same geometry, aimed five blocks out. 5 > radius 4.
        assertThat(RingPass.crosses(new Vec3(9.4, 69.2, -15.0), new Vec3(10.6, 70.8, -15.0), TILTED)).isFalse();
    }

    @Test
    void rejectsAZeroLengthStep() {
        // A stationary player produces from == to every tick. Without a guard the denominator is
        // zero and the parallel check is the only thing standing between that and a NaN.
        Vec3 standingStill = new Vec3(0.0, 64.0, 100.0);

        assertThat(RingPass.crosses(standingStill, standingStill, FLAT)).isFalse();
    }

    // --- Fixture sweep additions (task-2 step 6) ---
    //
    // In every case above, the player's y-coordinate is fixed at FLAT's own centre y (64.0), so the
    // rim check only ever measures an x-deviation; a distance formula that dropped the y term (or
    // swapped it for something else) would still pass every test above. These two add a y-offset
    // together with the existing x-offset so the rim check must combine both.
    //
    // 3-4-5 triangle: an offset of (3, -4) from the centre is exactly radius 5 away.

    @Test
    void countsAPassOnTheRimWithACombinedXAndYOffset() {
        assertThat(RingPass.crosses(new Vec3(3.0, 60.0, 99.0), new Vec3(3.0, 60.0, 101.0), FLAT)).isTrue();
    }

    @Test
    void rejectsAPassJustOutsideTheRimWithACombinedXAndYOffset() {
        assertThat(RingPass.crosses(new Vec3(3.001, 60.0, 99.0), new Vec3(3.001, 60.0, 101.0), FLAT)).isFalse();
    }

    // In every TILTED case above, from.z equals to.z, so the intersection's z-coordinate is never
    // actually interpolated by t — it is the same value whichever endpoint it is copied from. TILTED's
    // normal has no z component, so z is otherwise free; these two give the step a genuine z-delta and
    // land the offset from centre (which TILTED's other tests only ever produce along z, see above)
    // on that interpolated value instead of a value shared by both endpoints.
    //
    // t stays 0.5 (x/y unchanged from countsAPassThroughATiltedRingsCentre); z runs from -18 to -14,
    // so the midpoint is -16 — 4 above the centre's -20, exactly the tilted ring's radius.

    @Test
    void countsAPassOnTheRimOfATiltedRingThroughAGenuineZStep() {
        Vec3 from = new Vec3(9.4, 69.2, -18.0);
        Vec3 to = new Vec3(10.6, 70.8, -14.0);

        assertThat(RingPass.crosses(from, to, TILTED)).isTrue();
    }

    @Test
    void rejectsAPassJustOutsideTheRimOfATiltedRingThroughAGenuineZStep() {
        Vec3 from = new Vec3(9.4, 69.2, -18.0);
        Vec3 to = new Vec3(10.6, 70.8, -13.996);

        assertThat(RingPass.crosses(from, to, TILTED)).isFalse();
    }
}
