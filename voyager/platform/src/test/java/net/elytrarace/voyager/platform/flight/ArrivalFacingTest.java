package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.math.Vec3;
import net.minestom.server.coordinate.Pos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The facing a racer has when placed at a map's start: looking at the centre of the first ring. Plain numbers in, a
 * {@link Pos} out, so no server is needed and each test is one behaviour.
 */
class ArrivalFacingTest {

    private static final Vec3 ELYTRA_RACE_BLUE_AND_RED_SPAWN = new Vec3(109.0, -62.0, 54.0);
    private static final Vec3 ELYTRA_RACE_BLUE_AND_RED_RING_ZERO = new Vec3(85.0, -54.0, 54.0);

    @Test
    void aRacerFacingTheFirstRingOfTheShippedMapHasYaw90() {
        Pos facing = ArrivalFacing.at(ELYTRA_RACE_BLUE_AND_RED_SPAWN, ELYTRA_RACE_BLUE_AND_RED_RING_ZERO);

        assertThat(facing.yaw())
                .describedAs("ring 0 lies 24 blocks west of the spawn, which is yaw 90 in Minecraft")
                .isCloseTo(90.0f, within(1.0f));
    }

    @Test
    void thePitchLooksUpWhenTheFirstRingIsAboveTheSpawn() {
        Pos facing = ArrivalFacing.at(ELYTRA_RACE_BLUE_AND_RED_SPAWN, ELYTRA_RACE_BLUE_AND_RED_RING_ZERO);

        assertThat(facing.pitch())
                .describedAs("ring 0 is 8 blocks higher over 24 blocks, so the pitch is -atan(8 / 24) and looks up")
                .isCloseTo(-18.4349f, within(0.01f));
    }

    @Test
    void aRingDirectlyAboveTheSpawnDefinesAFacingInsteadOfFailing() {
        Pos facing = ArrivalFacing.at(new Vec3(0.0, 60.0, 0.0), new Vec3(0.0, 70.0, 0.0));

        assertThat(facing.pitch())
                .describedAs("with no horizontal offset the racer looks straight up")
                .isCloseTo(-90.0f, within(0.01f));
        assertThat(facing.yaw())
                .describedAs("the yaw of a vertical look is a finite number, not NaN")
                .isFinite();
    }

    @Test
    void theFacingKeepsThePositionOfTheSpawn() {
        Pos facing = ArrivalFacing.at(ELYTRA_RACE_BLUE_AND_RED_SPAWN, ELYTRA_RACE_BLUE_AND_RED_RING_ZERO);

        assertThat(facing.x()).isEqualTo(109.0);
        assertThat(facing.y()).isEqualTo(-62.0);
        assertThat(facing.z()).isEqualTo(54.0);
    }
}
