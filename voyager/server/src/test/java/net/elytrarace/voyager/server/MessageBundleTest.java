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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern PLACEHOLDER = Pattern.compile("<arg:\\d+>");

    private static VoyagerTranslator translator;

    @BeforeAll
    static void installTheShippedBundle() {
        translator = VoyagerTranslator.fromClasspath(VoyagerTranslator.BUNDLE);
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
    void theWaitingLineNamesTheOnlineRacersAndTheNeeded() {
        assertThat(render(Messages.lobbyWaiting(1, 2)))
                .isEqualTo("Waiting for racers: 1 of 2 online");
    }

    @Test
    void theLobbyCountdownIsWholeSeconds() {
        assertThat(render(Messages.lobbyCountdown(Duration.ofSeconds(20))))
                .isEqualTo("Cup starts in 20 s");
    }

    @Test
    void theCancelledLineSaysWhyAndHowManyAreOnline() {
        assertThat(render(Messages.lobbyCancelled(1, 2)))
                .isEqualTo("» Start cancelled: 1 of 2 racers online. Waiting for more.");
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
        assertThat(render(Messages.mapResultNextGoal(Duration.ofMillis(800), MedalTier.GOLD)))
                .isEqualTo("0.8 s faster for GOLD");
        assertThat(render(Messages.mapResultSubtitleDnf(23, 35)))
                .isEqualTo("23 of 35 rings");
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
                Messages.mapResultNextGoal(Duration.ZERO, MedalTier.GOLD),
                Messages.mapResultSubtitleDnf(1, 2),
                Messages.ringMissed(1),
                Messages.playersJoined(2, 3, 4),
                Messages.cupTitle(),
                Messages.cupSubtitle(1, 2),
                Messages.cupHeading("c"),
                Messages.cupRow(1, Messages.racerName("n"), 1, 1, 1, Optional.empty()),
                Messages.departed(),
                Messages.medal(MedalTier.DIAMOND),
                Messages.medal(MedalTier.DNF),
                Messages.cupRestarted("c"),
                Messages.skipTaken(),
                Messages.skipRefused(),
                Messages.statsProfileHeader("Alex"),
                Messages.statsProfileCups(2),
                Messages.statsProfileWins(1),
                Messages.statsProfileMedals(1, 0, 0, 2),
                Messages.statsProfileFlight(90_000L),
                Messages.statsProfileBest("ridge", 61_200L),
                Messages.statsProfileNone("Alex"),
                Messages.statsUnknownPlayer("Nobody"),
                Messages.statsLeaderboardHeader("ridge"),
                Messages.statsLeaderboardEntry(1, "Alex", 61_200L),
                Messages.statsLeaderboardEmpty("ridge"),
                Messages.statsUnknownMap("nowhere"),
                Messages.statsNotRecorded(),
                Messages.statsUnavailable(),
                Messages.statsUsage());

        for (Component message : everyMessage) {
            String rendered = render(message);

            assertThat(rendered).as("rendered text").isNotBlank();
            assertThat(rendered).as("a key that resolved to nothing").doesNotContain("voyager.");
            assertThat(rendered).as("an unresolved MiniMessage tag").doesNotContain("<");
            assertThat(rendered).as("a MessageFormat placeholder").doesNotContain("{0}");
        }
    }


    // ---------------------------------------------------------------------------------------
    // Statistics
    // ---------------------------------------------------------------------------------------

    @Test
    void theLeaderboardLineShowsRankNameAndTheTimeWithThreeDecimals() {
        assertThat(render(Messages.statsLeaderboardEntry(1, "Alex", 61_200L)))
                .isEqualTo("1. Alex \u00b7 61.200 s");
    }

    @Test
    void theFlightLineSplitsTheGlidingTimeIntoHoursMinutesAndSeconds() {
        assertThat(render(Messages.statsProfileFlight(3_723_400L)))
                .isEqualTo("Flight time: 1 h 2 min 3 s");
    }

    @Test
    void everyStatisticsKeyHasTheSamePlaceholdersInBothLocales() throws IOException {
        Properties english = bundle("/voyager_en_US.properties");
        Properties german = bundle("/voyager_de_DE.properties");
        List<String> keys = english.stringPropertyNames().stream()
                .filter(key -> key.startsWith("stats."))
                .sorted()
                .toList();

        assertThat(keys).as("the statistics keys, one line each").hasSize(15);
        assertThat(german.stringPropertyNames().stream().filter(key -> key.startsWith("stats.")).sorted().toList())
                .as("the German bundle names the same statistics keys")
                .isEqualTo(keys);
        for (String key : keys) {
            assertThat(placeholders(german.getProperty(key)))
                    .as("the placeholders of %s in de_DE", key)
                    .isEqualTo(placeholders(english.getProperty(key)));
        }
    }

    private static Properties bundle(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = MessageBundleTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("the bundle %s is on the classpath", resource).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    /** The {@code <arg:N>} placeholders of one line, in the order they appear. */
    private static List<String> placeholders(String line) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(line);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }
}

