package net.elytrarace.voyager.platform.hud;

import net.kyori.adventure.key.Key;
import net.elytrarace.voyager.api.race.MedalBrackets;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boss bar's colour: one fixed colour, whichever medal band the clock is inside.
 *
 * <p>The bar is the ring progress and the title names the medal, so the bar's colour carries no
 * medal. Before this rule the bar took the colour of the band, which put a blue bar under a title
 * that was also blue.
 */
@EnvTest
class RaceHudBossBarTest {

    private static final Duration REFERENCE = Duration.ofSeconds(60);
    private static final int RING_COUNT = 35;

    private Player createPlayer(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return env.createPlayer(instance, new Pos(0, 60, 0));
    }

    private static HudState atElapsed(int gameTick, int ringsPassed, Duration elapsed) {
        MedalOutlook outlook = MedalCountdown.outlook(elapsed, REFERENCE, MedalBrackets.DEFAULT);
        return new HudState(gameTick, elapsed, ringsPassed, RING_COUNT, gameTick - 2, 2, 3,
                Key.key("elytrarace", "skylift"), "skylift", outlook);
    }

    /**
     * 23.4 s into a 60 s reference is inside diamond; 70 s is past gold and inside silver. The two
     * samples are in different bands, so a colour that followed the band would differ between them.
     */
    @Test
    void theBossBarColourDoesNotChangeWhenTheMedalBandChanges(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();

        hud.render(racer, atElapsed(468, 12, Duration.ofMillis(23_400)));
        BossBar.Color inDiamond = hud.barOf(racer.getUuid()).orElseThrow().color();

        hud.render(racer, atElapsed(1400, 20, Duration.ofSeconds(70)));
        BossBar.Color inSilver = hud.barOf(racer.getUuid()).orElseThrow().color();

        assertThat(inSilver).as("the colour inside silver").isEqualTo(inDiamond);
    }

    @Test
    void theBossBarIsGreenWhateverBandTheRacerIsIn(Env env) {
        Player racer = createPlayer(env);
        RaceHud hud = new RaceHud();

        hud.render(racer, atElapsed(468, 12, Duration.ofMillis(23_400)));

        assertThat(hud.barOf(racer.getUuid()).orElseThrow().color()).isEqualTo(BossBar.Color.GREEN);
    }
}
