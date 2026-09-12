package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.platform.text.Palette;
import net.elytrarace.voyager.race.scoring.MedalCountdown;
import net.elytrarace.voyager.race.scoring.MedalOutlook;
import net.kyori.adventure.bossbar.BossBar;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The two live surfaces: when they refresh, what the bar says, and what happens when a map ends.
 *
 * <h2>What is asserted and what is not</h2>
 *
 * <p>The decisions live in three places and all three are pinned here: the 10 Hz cadence, the
 * eight-tick window the ring counter stays green for, and the fill. The boss bar itself is read back
 * through a narrow package-private seam, because a bar leaves no other trace — Adventure addresses
 * it to a viewer and Minestom turns it into a packet, and asserting the packet would be asserting
 * the wire format rather than the decision.
 */
@EnvTest
class RaceHudTest {

    private static final Duration REFERENCE = Duration.ofSeconds(60);
    private static final int RING_COUNT = 35;

    private Player createPlayer(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0, 60, 0));
    }

    private static HudState at(int gameTick, int ringsPassed, int lastRingTick) {
        Duration elapsed = Duration.ofMillis(gameTick * 50L);
        MedalOutlook outlook = MedalCountdown.outlook(elapsed, REFERENCE, MedalBrackets.DEFAULT);
        return new HudState(gameTick, elapsed, ringsPassed, RING_COUNT, lastRingTick, 2, 3,
                "skylift", outlook);
    }

    // ---------------------------------------------------------------------------------------
    // Cadence
    // ---------------------------------------------------------------------------------------

    /**
     * 10 Hz, because both surfaces show tenths of a second and a tenths display refreshed slower
     * than that skips digits. Asserted tick by tick over a whole second rather than at one sample,
     * so a cadence of 1, 3 or 4 fails here rather than only looking slightly different in a video.
     */
    @Test
    void bothSurfacesRefreshEveryOtherTick() {
        assertThat(RaceHud.REFRESH_INTERVAL_TICKS).isEqualTo(2);
        for (int tick = 0; tick <= 20; tick++) {
            assertThat(RaceHud.refreshesOn(tick))
                    .as("tick %d", tick)
                    .isEqualTo(tick % 2 == 0);
        }
    }

    @Test
    void aTickThatIsNotARefreshTickSendsNothing(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();

        hud.render(racer, at(3, 1, 3));

        assertThat(hud.barOf(racer.getUuid())).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // The ring flash
    // ---------------------------------------------------------------------------------------

    /**
     * Eight ticks, asserted on both sides of its own edge. The counter has to be green long enough
     * to read as an acknowledgement of <em>that</em> ring, and back to white before the next one
     * 1.4 seconds later — a flash one tick too long is invisible here and a flash that never ends is
     * a counter that is simply green.
     */
    @Test
    void theCounterIsGreenForExactlyEightTicksAfterARing() {
        assertThat(RaceHud.RING_FLASH_TICKS).isEqualTo(8);
        assertThat(RaceHud.flashing(at(100, 5, 100))).isTrue();
        assertThat(RaceHud.flashing(at(107, 5, 100))).isTrue();
        assertThat(RaceHud.flashing(at(108, 5, 100))).isFalse();
        assertThat(RaceHud.flashing(at(200, 5, 100))).isFalse();
    }

    @Test
    void aRacerWhoHasPassedNoRingIsNotFlashing() {
        assertThat(RaceHud.flashing(at(4, 0, 0))).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // The bar
    // ---------------------------------------------------------------------------------------

    @Test
    void theFillIsTheFractionOfTheCourseBehindTheRacer() {
        assertThat(RaceHud.fill(0, 35)).isEqualTo(0.0f);
        assertThat(RaceHud.fill(12, 35)).isCloseTo(12f / 35f, within(1e-6f));
        assertThat(RaceHud.fill(35, 35)).isEqualTo(1.0f);
    }

    @Test
    void armingPutsAnEmptyBarUpBeforeTheRaceStarts(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();

        hud.arm(racer, at(0, 0, 0));

        BossBar bar = hud.barOf(racer.getUuid()).orElseThrow();
        assertThat(bar.progress()).isEqualTo(0.0f);
        assertThat(bar.color()).isEqualTo(Palette.bossBarColour(MedalTier.DIAMOND));
        assertThat(bar.overlay()).isEqualTo(BossBar.Overlay.PROGRESS);
    }

    @Test
    void theFillAdvancesWithTheRingsPassed(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();
        hud.arm(racer, at(0, 0, 0));

        hud.render(racer, at(468, 12, 461));

        assertThat(hud.barOf(racer.getUuid()).orElseThrow().progress())
                .isCloseTo(12f / 35f, within(1e-6f));
    }

    /**
     * The bar changes band with the clock and not with anything else. 23.4 s into a 60 s reference
     * is still diamond; 70 s is gold gone and silver holding, which is a different colour.
     */
    @Test
    void theBarTakesTheColourOfTheBandTheClockIsInside(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();

        hud.render(racer, at(468, 12, 461));
        assertThat(hud.barOf(racer.getUuid()).orElseThrow().color())
                .isEqualTo(Palette.bossBarColour(MedalTier.DIAMOND));

        hud.render(racer, at(1400, 20, 1390));
        assertThat(hud.barOf(racer.getUuid()).orElseThrow().color())
                .isEqualTo(Palette.bossBarColour(MedalTier.SILVER));
    }

    @Test
    void oneBarPerRacerAndTheSecondRenderReusesTheFirstOne(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();

        hud.arm(racer, at(0, 0, 0));
        BossBar first = hud.barOf(racer.getUuid()).orElseThrow();
        hud.render(racer, at(20, 1, 18));

        assertThat(hud.barOf(racer.getUuid())).containsSame(first);
    }

    @Test
    void aMapEndingTakesTheBarDown(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();
        hud.arm(racer, at(0, 0, 0));

        hud.standDown(racer);

        assertThat(hud.barOf(racer.getUuid())).isEmpty();
    }

    @Test
    void standingDownARacerWhoNeverHadABarIsNotAFailure(Env env) {
        Player racer = createPlayer(env);

        new RaceHud().standDown(racer);

        assertThat(new RaceHud().barOf(racer.getUuid())).isEmpty();
    }

    @Test
    void aDisconnectedRacersBarIsForgotten(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();
        hud.arm(racer, at(0, 0, 0));

        hud.forget(racer.getUuid());

        assertThat(hud.barOf(racer.getUuid())).isEqualTo(Optional.empty());
    }
}
