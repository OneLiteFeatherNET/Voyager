package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.race.scoring.MedalOutlook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What each message is keyed on and what it carries, checked one call at a time.
 *
 * <p>The wording itself is the bundle's and is rendered end to end by {@code voyager-server}'s
 * {@code MessageBundleTest}. What can go wrong <em>here</em> is an argument in the wrong slot, a
 * number formatted the wrong way, or two different messages sharing a key — all of which produce a
 * line that reads plausibly and says something false.
 */
class MessagesTest {

    private static TranslatableComponent translatable(Component component) {
        assertThat(component).isInstanceOf(TranslatableComponent.class);
        return (TranslatableComponent) component;
    }

    /**
     * Each argument as something a test can name: plain text for a value, {@code @key} for an
     * argument that is itself a translation. Rendering a nested translatable through a serializer
     * here would only prove what the plain-text flattener does with an untranslated key, which is
     * not a fact about this class.
     */
    private static List<String> argumentsOf(Component component) {
        return translatable(component).arguments().stream()
                .map(argument -> (Component) argument.value())
                .map(value -> value instanceof TranslatableComponent nested
                        ? "@" + nested.key()
                        : PlainTextComponentSerializer.plainText().serialize(value))
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // The flight HUD
    // ---------------------------------------------------------------------------------------

    @Test
    void theFlightHudCarriesTheClockTheCounterTheTotalAndThePad() {
        Component hud = Messages.flightHud(Duration.ofMillis(23_400), 12, 35, false);

        assertThat(translatable(hud).key()).isEqualTo("voyager.hud.flight");
        assertThat(argumentsOf(hud)).containsExactly("0:23.4", "12", "35", "      ");
    }

    /**
     * The pad is exactly as wide as the clock, which is what puts the counter on the screen's
     * centreline rather than the gap between the two fields. A pad of a fixed literal width would
     * drift the moment the clock's own width changed.
     */
    @Test
    void thePadIsTheSameWidthAsTheClockItBalances() {
        for (Duration elapsed : List.of(Duration.ZERO, Duration.ofMillis(23_400),
                Duration.ofSeconds(300), Duration.ofSeconds(700))) {
            List<String> arguments = argumentsOf(Messages.flightHud(elapsed, 1, 35, false));

            assertThat(arguments.get(3)).hasSameSizeAs(arguments.getFirst());
            assertThat(arguments.get(3)).isBlank();
        }
    }

    /**
     * The flash is a colour change and not a width change. A counter that grew a {@code +10} suffix
     * would shove the clock sideways every 1.7 seconds directly under the crosshair.
     */
    @Test
    void aRingPassRecoloursTheCounterWithoutChangingTheLayout() {
        Component quiet = Messages.flightHud(Duration.ofMillis(23_400), 12, 35, false);
        Component flashing = Messages.flightHud(Duration.ofMillis(23_400), 12, 35, true);

        assertThat(argumentsOf(flashing)).isEqualTo(argumentsOf(quiet));
        assertThat(counterColour(quiet)).isEqualTo(Palette.VALUE);
        assertThat(counterColour(flashing)).isEqualTo(Palette.OK);
    }

    private static Object counterColour(Component hud) {
        return ((Component) translatable(hud).arguments().get(1).value()).color();
    }

    // ---------------------------------------------------------------------------------------
    // The boss bar
    // ---------------------------------------------------------------------------------------

    @Test
    void theBossBarCarriesTheBandAndTheTimeLeftInIt() {
        MedalOutlook diamond = new MedalOutlook(MedalTier.DIAMOND, Optional.of(Duration.ofMillis(36_600)));

        Component bar = Messages.bossBarTitle(1, 3, "skylift", diamond);

        assertThat(translatable(bar).key()).isEqualTo("voyager.hud.bossbar");
        assertThat(argumentsOf(bar)).containsExactly("1", "3", "skylift", "@voyager.medal.diamond", "0:36.6");
    }

    /**
     * The band below every bracket has nothing under it to fall into, so it gets a shorter line
     * rather than a countdown reading zero forever.
     */
    @Test
    void theLastBandHasNoCountdownAtAll() {
        MedalOutlook finish = new MedalOutlook(MedalTier.FINISH, Optional.empty());

        Component bar = Messages.bossBarTitle(2, 3, "skylift", finish);

        assertThat(translatable(bar).key()).isEqualTo("voyager.hud.bossbar.last");
        assertThat(argumentsOf(bar)).containsExactly("2", "3", "skylift", "@voyager.medal.finish");
    }

    // ---------------------------------------------------------------------------------------
    // Start, finish, results
    // ---------------------------------------------------------------------------------------

    @Test
    void theCountdownSubtitleNamesTheCourseAndItsTargetWithoutATenthsDigit() {
        Component subtitle = Messages.countdownSubtitle("skylift", 35, Duration.ofSeconds(60));

        assertThat(translatable(subtitle).key()).isEqualTo("voyager.countdown.subtitle");
        assertThat(argumentsOf(subtitle)).containsExactly("skylift", "35", "1:00");
    }

    @Test
    void theMapBannerCountsMapsAsAPlayerDoes() {
        Component banner = Messages.mapBanner(1, 3, "skylift", 35, Duration.ofSeconds(60));

        assertThat(argumentsOf(banner)).containsExactly("1", "3", "skylift", "35", "1:00");
    }

    @Test
    void aFinishCarriesTheCourseAndTheTimeToATenth() {
        Component finished = Messages.finished("skylift", Duration.ofMillis(58_400));

        assertThat(argumentsOf(finished)).containsExactly("skylift", "0:58.4");
    }

    @Test
    void aMedalResultBreaksTheScoreIntoWhatEarnedIt() {
        Component result = Messages.mapResult("skylift", 350, 60, MedalTier.GOLD, 10, 420);

        assertThat(translatable(result).key()).isEqualTo("voyager.result.map.medal");
        assertThat(argumentsOf(result)).containsExactly("skylift", "350", "60", "@voyager.medal.gold", "10", "420");
    }

    /**
     * A DNF is told how far it got, not only that it failed. The ring count is in the line because
     * "23 of 35" is a fact a racer can act on and "DNF" on its own is not.
     */
    @Test
    void aDnfIsToldHowFarItGot() {
        Component result = Messages.mapResultDnf("skylift", 23, 35, 230);

        assertThat(translatable(result).key()).isEqualTo("voyager.result.map.dnf");
        assertThat(argumentsOf(result)).containsExactly("skylift", "23", "35", "230");
    }

    @Test
    void theResultSubtitlesCarryTheTimeOrTheProgressAndThePoints() {
        assertThat(argumentsOf(Messages.mapResultSubtitle(Duration.ofMillis(58_400), 420)))
                .containsExactly("0:58.4", "420");
        assertThat(argumentsOf(Messages.mapResultSubtitleDnf(23, 35, 230)))
                .containsExactly("23", "35", "230");
    }

    @Test
    void aStandingsRowCarriesThePlaceTheNameThePointsTheMapsAndTheBest() {
        Component row = Messages.cupRow(2, Messages.racerName("Bob"), 380, 3, 3,
                Optional.of(Duration.ofMillis(56_100)));

        assertThat(argumentsOf(row)).containsExactly("2", "Bob", "380", "3", "3", "0:56.1");
    }

    /**
     * A racer who finished nothing has no best time, and the row says so with a dash rather than
     * with a zero — which would read as a run of no length rather than as no run.
     */
    @Test
    void aRacerWithNoFinishedMapGetsAPlaceholderRatherThanAZero() {
        Component row = Messages.cupRow(3, Messages.departed(), 150, 0, 3, Optional.empty());

        assertThat(argumentsOf(row))
                .containsExactly("3", "@voyager.result.cup.departed", "150", "0", "3",
                        "@voyager.result.cup.notime");
    }

    @Test
    void aDepartedRacerIsNamedRatherThanPrintedAsAUuid() {
        assertThat(translatable(Messages.departed()).key()).isEqualTo("voyager.result.cup.departed");
        assertThat(translatable(Messages.departed()).arguments()).isEmpty();
    }

    @Test
    void everyMedalHasItsOwnKey() {
        Set<String> keys = new HashSet<>();
        for (MedalTier tier : MedalTier.values()) {
            keys.add(translatable(Messages.medal(tier)).key());
        }

        assertThat(keys).hasSize(MedalTier.values().length);
        assertThat(keys).allMatch(key -> key.startsWith("voyager.medal."));
    }

    // ---------------------------------------------------------------------------------------
    // Everything at once
    // ---------------------------------------------------------------------------------------

    /**
     * No two distinct messages share a key. Two would mean one bundle line trying to be two
     * sentences with two different argument lists, which renders as whichever of them was written
     * last plus a missing-argument failure for the other.
     */
    @Test
    void noTwoMessagesShareAKey() {
        List<Component> everyMessage = List.of(
                Messages.flightHud(Duration.ZERO, 0, 35, false),
                Messages.bossBarTitle(1, 1, "m", new MedalOutlook(MedalTier.GOLD, Optional.of(Duration.ZERO))),
                Messages.bossBarTitle(1, 1, "m", new MedalOutlook(MedalTier.FINISH, Optional.empty())),
                Messages.countdownDigit(3),
                Messages.countdownSubtitle("m", 1, Duration.ofSeconds(1)),
                Messages.go(),
                Messages.mapBanner(1, 1, "m", 1, Duration.ofSeconds(1)),
                Messages.finished("m", Duration.ZERO),
                Messages.joinedMidCup(),
                Messages.mapResult("m", 1, 2, MedalTier.GOLD, 3, 6),
                Messages.mapResultDnf("m", 1, 2, 3),
                Messages.mapResultSubtitle(Duration.ZERO, 1),
                Messages.mapResultSubtitleDnf(1, 2, 3),
                Messages.cupTitle(),
                Messages.cupSubtitle(1, 2),
                Messages.cupHeading("c"),
                Messages.cupRow(1, Messages.racerName("n"), 1, 1, 1, Optional.empty()),
                Messages.departed(),
                Messages.cupRestarted("c"),
                Messages.skipTaken(),
                Messages.skipRefused());

        List<String> keys = everyMessage.stream().map(m -> translatable(m).key()).toList();

        assertThat(keys).doesNotHaveDuplicates();
        assertThat(keys).allMatch(key -> key.startsWith("voyager."));
    }

    /**
     * A racer's own name is the one user-facing string that cannot be translated, which is why it
     * has a factory here at all — so that no caller outside this package has to build a component.
     * It carries no colour of its own, so the line it lands in decides.
     */
    @Test
    void aRacerNameIsPlainAndUncolouredSoTheLineDecides() {
        Component name = Messages.racerName("Bob");

        assertThat(PlainTextComponentSerializer.plainText().serialize(name)).isEqualTo("Bob");
        assertThat(name.color()).isNull();
    }

    // ---------------------------------------------------------------------------------------
    // The waiting lobby
    // ---------------------------------------------------------------------------------------

    @Test
    void waitingLineNamesOnlineAndNeeded() {
        Component waiting = Messages.lobbyWaiting(1, 2);

        assertThat(translatable(waiting).key()).isEqualTo("voyager.lobby.waiting");
        assertThat(argumentsOf(waiting)).containsExactly("1", "2");
    }

    /**
     * The countdown is shown in whole seconds, rounded up, so 20 s reads 20 and 6 s reads 6. A tick count would
     * read 400 for the same lobby, which is the unit error issue #101 reported.
     */
    @Test
    void countdownShowsWholeSecondsOfTheLobby() {
        assertThat(argumentsOf(Messages.lobbyCountdown(Duration.ofSeconds(20)))).containsExactly("20");
        assertThat(argumentsOf(Messages.lobbyCountdown(Duration.ofSeconds(6)))).containsExactly("6");
    }

    @Test
    void countdownRoundsAPartialSecondUp() {
        assertThat(argumentsOf(Messages.lobbyCountdown(Duration.ofMillis(5_400)))).containsExactly("6");
    }

    @Test
    void cancelledLineNamesOnlineAndNeeded() {
        Component cancelled = Messages.lobbyCancelled(1, 2);

        assertThat(translatable(cancelled).key()).isEqualTo("voyager.lobby.cancelled");
        assertThat(argumentsOf(cancelled)).containsExactly("1", "2");
    }
}
