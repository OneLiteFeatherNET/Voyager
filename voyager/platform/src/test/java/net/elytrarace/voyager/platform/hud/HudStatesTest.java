package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.race.cup.MapFigures;
import net.elytrarace.voyager.race.scoring.MedalOutlook;
import net.elytrarace.voyager.api.race.MedalTier;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The one mapping from a map's figures to the HUD state drawn from them. */
class HudStatesTest {

    @Test
    void mapsEveryFigureToTheMatchingFieldOfTheHudState() {
        // The figures of the racing-boost scenario: a racer one ring in, 1.5 seconds into map one of two.
        MapFigures figures = new MapFigures(30, Duration.ofMillis(1500), 1, 2, 24, 1, 2, "ridge-run",
                new MedalOutlook(MedalTier.GOLD, Optional.of(Duration.ofSeconds(3))));

        HudState state = HudStates.of(figures);

        assertThat(state.gameTick()).isEqualTo(figures.gameTick());
        assertThat(state.elapsed()).isEqualTo(figures.elapsed());
        assertThat(state.ringsPassed()).isEqualTo(figures.ringsPassed());
        assertThat(state.ringCount()).isEqualTo(figures.ringCount());
        assertThat(state.lastRingGameTick()).isEqualTo(figures.lastRingTick());
        assertThat(state.mapNumber()).isEqualTo(figures.mapNumber());
        assertThat(state.mapCount()).isEqualTo(figures.mapCount());
        assertThat(state.mapName()).isEqualTo(figures.mapName());
        assertThat(state.outlook()).isEqualTo(figures.outlook());
    }
}
