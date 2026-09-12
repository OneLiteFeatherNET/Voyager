package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.text.VoyagerTranslator;
import net.elytrarace.voyager.race.scoring.MedalOutlook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped bundle, rendered the way a player's client would see it.
 *
 * <h2>Why this is a rendering test and not a key-coverage test</h2>
 *
 * <p>Asserting that a properties file contains a key proves the file contains a key. What a player
 * actually gets is a {@code Component.translatable} rendered through MiniMessage with its arguments
 * bound and the palette applied, and every interesting failure lives in that pipeline rather than in
 * the file: a key nobody added renders as its own dotted name, an argument index one too high throws
 * at render time, a mistyped palette tag survives as visible {@code <c-speeed>} text, and a value
 * written with {@code {0}} prints the braces. So this renders the real calls the server makes,
 * against the real bundle, and reads the sentence back.
 *
 * <p>It is also where the <strong>wording</strong> is pinned. These are the strings a player reads;
 * changing one should be a deliberate edit to a test that says what it used to say.
 */
class MessageBundleTest {

    private static VoyagerTranslator translator;

    @BeforeAll
    static void installTheShippedBundle() {
        translator = VoyagerTranslator.fromClasspath();
        translator.install();
    }

    @AfterAll
    static void removeIt() {
        GlobalTranslator.translator().removeSource(translator);
    }

    private static String render(Component component) {
        return PlainTextComponentSerializer.plainText()
                .serialize(GlobalTranslator.render(component, Locale.US));
    }

    @Test
    void theBundleIsOnTheClasspathAndHoldsEveryMessage() {
        assertThat(translator.size()).isGreaterThan(20);
    }

    // ---------------------------------------------------------------------------------------
    // In the air
    // ---------------------------------------------------------------------------------------

    @Test
    void theFlightHudIsTheClockThenTheCounterThenThePadThatCentresIt() {
        assertThat(render(Messages.flightHud(Duration.ofMillis(23_400), 12, 35, false)))
                .isEqualTo("0:23.4   12/35         ");
    }

    @Test
    void theBossBarNamesTheMapTheCourseAndTheBandWithTheTimeLeftInIt() {
        MedalOutlook diamond = new MedalOutlook(MedalTier.DIAMOND, Optional.of(Duration.ofMillis(36_600)));

        assertThat(render(Messages.bossBarTitle(1, 3, "skylift", diamond)))
                .isEqualTo("Map 1/3 · skylift · DIAMOND 0:36.6");
    }

    @Test
    void theBossBarDropsTheCountdownOnceThereIsNoMedalLeftToLose() {
        MedalOutlook finish = new MedalOutlook(MedalTier.FINISH, Optional.empty());

        assertThat(render(Messages.bossBarTitle(2, 3, "skylift", finish)))
                .isEqualTo("Map 2/3 · skylift · FINISH");
    }

    // ---------------------------------------------------------------------------------------
    // The start
    // ---------------------------------------------------------------------------------------

    @Test
    void theCountdownIsThreeDigitsAndAWord() {
        assertThat(render(Messages.countdownDigit(3))).isEqualTo("3");
        assertThat(render(Messages.countdownDigit(2))).isEqualTo("2");
        assertThat(render(Messages.countdownDigit(1))).isEqualTo("1");
        assertThat(render(Messages.go())).isEqualTo("GO");
    }

    @Test
    void theCountdownSubtitleIsTheWholeBriefingInOneLine() {
        assertThat(render(Messages.countdownSubtitle("skylift", 35, Duration.ofSeconds(60))))
                .isEqualTo("skylift · 35 rings · target 1:00");
    }

    @Test
    void theMapBannerCarriesTheBroadcastPrefix() {
        assertThat(render(Messages.mapBanner(1, 3, "skylift", 35, Duration.ofSeconds(60))))
                .isEqualTo("» Map 1/3 · skylift — 35 rings · target 1:00");
    }

    // ---------------------------------------------------------------------------------------
    // Results
    // ---------------------------------------------------------------------------------------

    @Test
    void aFinishIsOneSentenceWithTheCourseAndTheTime() {
        assertThat(render(Messages.finished("skylift", Duration.ofMillis(58_400))))
                .isEqualTo("Finished skylift in 0:58.4");
    }

    @Test
    void aMapResultShowsWhereEveryPointCameFrom() {
        assertThat(render(Messages.mapResult("skylift", 350, 60, MedalTier.DIAMOND, 10, 420)))
                .isEqualTo("skylift — 350 from rings · 60 for DIAMOND · 10 for placement = 420");
    }

    @Test
    void aDnfIsToldHowFarItGotRatherThanOnlyThatItFailed() {
        assertThat(render(Messages.mapResultDnf("skylift", 23, 35, 230)))
                .isEqualTo("skylift — DNF. 23 of 35 rings, 230 points.");
    }

    @Test
    void theResultTitleIsOneWordAndOneLine() {
        assertThat(render(Messages.medal(MedalTier.GOLD))).isEqualTo("GOLD");
        assertThat(render(Messages.mapResultSubtitle(Duration.ofMillis(64_800), 420)))
                .isEqualTo("1:04.8  ·  420 points");
        assertThat(render(Messages.mapResultSubtitleDnf(23, 35, 230)))
                .isEqualTo("23 of 35 rings  ·  230 points");
    }

    @Test
    void everyMedalHasAWordOfItsOwn() {
        assertThat(MedalTier.values()).allSatisfy(tier ->
                assertThat(render(Messages.medal(tier))).isEqualTo(tier.name()));
    }

    @Test
    void theCupEndsWithATitleAndAStandingsBlock() {
        assertThat(render(Messages.cupTitle())).isEqualTo("CUP COMPLETE");
        assertThat(render(Messages.cupSubtitle(1, 420))).isEqualTo("place 1  ·  420 points");
        assertThat(render(Messages.cupHeading("test_cup"))).isEqualTo("» test_cup — final standings");
    }

    @Test
    void aStandingsRowReadsAsATable() {
        Component row = Messages.cupRow(2, Messages.racerName("Bob"), 380, 3, 3,
                Optional.of(Duration.ofMillis(56_100)));

        assertThat(render(row)).isEqualTo("  2  Bob  ·  380 points  ·  3/3 maps  ·  best 0:56.1");
    }

    /**
     * A racer who left before the standings were read out, and one who finished nothing. Between
     * them these two placeholders are the difference between a readable table and a line of
     * hexadecimal next to a zero that looks like a time.
     */
    @Test
    void aDepartedRacerWithNoFinishedMapIsStillAReadableRow() {
        Component row = Messages.cupRow(3, Messages.departed(), 150, 0, 3, Optional.empty());

        assertThat(render(row)).isEqualTo("  3  (left)  ·  150 points  ·  0/3 maps  ·  best —");
    }

    // ---------------------------------------------------------------------------------------
    // Everything else a player or an operator is told
    // ---------------------------------------------------------------------------------------

    @Test
    void aPlayerWhoJoinsIntoARunningCupIsToldWhatIsHappening() {
        assertThat(render(Messages.joinedMidCup()))
                .isEqualTo("» A cup is running. You join at the next map.");
    }

    @Test
    void theOperatorCommandsAnswerInWords() {
        assertThat(render(Messages.cupRestarted("test_cup")))
                .isEqualTo("Cup test_cup restarted, lobby skipped");
        assertThat(render(Messages.skipTaken())).isEqualTo("Ending the current map on the next tick");
        assertThat(render(Messages.skipRefused()))
                .isEqualTo("Nothing is racing — a skip is not banked for the next map");
    }

    /**
     * Nothing renders as its own key, and nothing leaks a tag. Both are what a missing key and a
     * mistyped palette tag look like, and both would otherwise ship — a translation that failed to
     * translate is not an exception, it is a sentence that reads like a stack trace.
     */
    @Test
    void noMessageRendersAsARawKeyOrLeavesATagBehind() {
        List<Component> everyMessage = List.of(
                Messages.flightHud(Duration.ZERO, 0, 35, false),
                Messages.flightHud(Duration.ZERO, 1, 35, true),
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
                Messages.medal(MedalTier.DIAMOND),
                Messages.medal(MedalTier.DNF),
                Messages.cupRestarted("c"),
                Messages.skipTaken(),
                Messages.skipRefused());

        for (Component message : everyMessage) {
            String rendered = render(message);

            assertThat(rendered).as("rendered text").isNotBlank();
            assertThat(rendered).as("a key that resolved to nothing").doesNotContain("voyager.");
            assertThat(rendered).as("an unresolved MiniMessage tag").doesNotContain("<");
            assertThat(rendered).as("a MessageFormat placeholder").doesNotContain("{0}");
        }
    }
}
