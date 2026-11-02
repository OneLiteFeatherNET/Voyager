package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.flow.RaceState;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boss bar's title while a racer waits between two maps. The title is decided from a race state and the cup's timings,
 * so each test builds its own state and needs no server and no clock.
 */
class WaitTitleTest {

    /** Lobby 20 s, race 60 s, results 8 s between maps: a full wait is 28 s. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(20), Duration.ofSeconds(60), Duration.ofSeconds(8), Duration.ofSeconds(20));

    @Test
    void theBossBarCountsDownFromTheFullWaitAfterAMap() {
        WaitBar atTheEnd = WaitBar.countingDown(new RaceState(RacePhase.END, 0, Duration.ZERO, false), TIMINGS, true, true)
                .orElseThrow();
        WaitBar aSecondLater = WaitBar.countingDown(
                new RaceState(RacePhase.END, 0, Duration.ofSeconds(1), false), TIMINGS, true, true).orElseThrow();

        assertThat(keyOf(atTheEnd.title())).isEqualTo("voyager.wait.bossbar.next");
        assertThat(argumentOf(atTheEnd.title()))
                .as("the full wait of 28 s is shown at the end of the map")
                .isEqualTo("28");
        assertThat(argumentOf(aSecondLater.title()))
                .as("and the number falls by one a second")
                .isEqualTo("27");
        assertThat(atTheEnd.progress()).as("nothing of the wait has passed yet").isEqualTo(0f);
    }

    @Test
    void theTitleIsAbsentBeforeTheFirstMap() {
        RaceState firstLobby = new RaceState(RacePhase.LOBBY, 0, Duration.ofSeconds(4), false);

        assertThat(WaitBar.countingDown(firstLobby, TIMINGS, false, true))
                .as("no wait runs before the first map of a cup, so no title is shown")
                .isEmpty();
    }

    @Test
    void theTitleIsAbsentAfterTheLastMap() {
        RaceState afterTheLast = new RaceState(RacePhase.END, 1, Duration.ZERO, true);

        assertThat(WaitBar.countingDown(afterTheLast, TIMINGS, true, false))
                .as("after the last map the cup's end screen takes over, and nothing is counted down")
                .isEmpty();
    }

    @Test
    void theTitleIsShownInTheLobbyOfAMapThatFollowsAPractice() {
        RaceState retryLobby = new RaceState(RacePhase.LOBBY, 0, Duration.ofSeconds(10), false);

        assertThat(WaitBar.countingDown(retryLobby, TIMINGS, true, true))
                .as("a practice retry is a wait like any other between two maps")
                .isPresent();
    }

    @Test
    void theTitleIsNotShownDuringAGame() {
        RaceState racing = new RaceState(RacePhase.GAME, 0, Duration.ofSeconds(3), false);

        assertThat(WaitBar.countingDown(racing, TIMINGS, true, true))
                .as("a game is not a wait: a racer still racing has the race bar")
                .isEmpty();
    }

    @Test
    void aRacerInTheNextMapBeforeThePhaseEndsReadsWaitingForTheRacersStillOnTheMap() {
        WaitBar two = WaitBar.waiting(2);
        WaitBar one = WaitBar.waiting(1);

        assertThat(keyOf(two.title())).isEqualTo("voyager.wait.bossbar.waiting.other");
        assertThat(argumentOf(two.title())).isEqualTo("2");
        assertThat(keyOf(one.title()))
                .as("one racer is named in the singular, so the sentence reads correctly")
                .isEqualTo("voyager.wait.bossbar.waiting.one");
    }

    private static String keyOf(Component title) {
        return ((TranslatableComponent) title).key();
    }

    private static String argumentOf(Component title) {
        Component argument = (Component) ((TranslatableComponent) title).arguments().getFirst().value();
        return PlainTextComponentSerializer.plainText().serialize(argument);
    }
}
