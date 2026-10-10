package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.race.scoring.MedalOutlook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The seam between the race and the screen, and the invariants that stop it carrying nonsense.
 *
 * <p>Every field in the fixture below is a different number, and none of them equals its own index
 * or any other field. A HUD value where "ring 3 of 3 on map 3 of 3" is the fixture cannot tell a
 * renderer that reads the map count from one that reads the ring count.
 */
class HudStateTest {

    private static final MedalOutlook DIAMOND =
            new MedalOutlook(MedalTier.DIAMOND, Optional.of(Duration.ofSeconds(37)));

    private static HudState state(int gameTick, int ringsPassed, int ringCount, int lastRingTick,
            int mapNumber, int mapCount) {
        return new HudState(gameTick, Duration.ofMillis(gameTick * 50L), ringsPassed, ringCount,
                lastRingTick, mapNumber, mapCount, "skylift", DIAMOND);
    }

    @Test
    void carriesEveryFieldTheSurfacesNeed() {
        HudState state = state(468, 12, 35, 461, 2, 3);

        assertThat(state.gameTick()).isEqualTo(468);
        assertThat(state.elapsed()).isEqualTo(Duration.ofMillis(23_400));
        assertThat(state.ringsPassed()).isEqualTo(12);
        assertThat(state.ringCount()).isEqualTo(35);
        assertThat(state.lastRingGameTick()).isEqualTo(461);
        assertThat(state.mapNumber()).isEqualTo(2);
        assertThat(state.mapCount()).isEqualTo(3);
        assertThat(state.mapName()).isEqualTo("skylift");
        assertThat(state.outlook()).isEqualTo(DIAMOND);
    }

    @Test
    void theStateAStartCountdownShowsIsLegal() {
        HudState armed = state(0, 0, 35, 0, 1, 1);

        assertThat(armed.gameTick()).isZero();
        assertThat(armed.lastRingGameTick()).isZero();
    }

    @Test
    void everyRingPassedIsLegalBecauseThatIsWhatFinishingIs() {
        assertThat(state(700, 35, 35, 700, 1, 1).ringsPassed()).isEqualTo(35);
    }

    @Test
    void refusesMoreRingsPassedThanTheCourseHas() {
        assertThatThrownBy(() -> state(700, 36, 35, 700, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rings passed");
    }

    @Test
    void refusesACourseWithNoRings() {
        assertThatThrownBy(() -> state(1, 0, 0, 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a course");
    }

    /**
     * The flash is computed from the difference between these two, so a last-ring tick in the future
     * would produce a negative age and a counter that is green forever or never.
     */
    @Test
    void refusesARingPassedOnATickThatHasNotBeenPlayed() {
        assertThatThrownBy(() -> state(10, 1, 35, 11, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has not been played");
    }

    @Test
    void refusesANegativeGameTick() {
        assertThatThrownBy(() -> new HudState(-1, Duration.ZERO, 0, 35, 0, 1, 1, "skylift", DIAMOND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gameTick");
    }

    @Test
    void refusesAMapNumberOutsideTheCupsOwnRotation() {
        assertThatThrownBy(() -> state(1, 0, 35, 0, 4, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map number");
        assertThatThrownBy(() -> state(1, 0, 35, 0, 0, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("map number");
    }

    @Test
    void refusesACupWithNoMaps() {
        assertThatThrownBy(() -> state(1, 0, 35, 0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one map");
    }
}
