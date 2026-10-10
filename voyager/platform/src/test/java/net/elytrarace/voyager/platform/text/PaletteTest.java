package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.api.race.MedalTier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The palette, and the two rules it exists to keep.
 *
 * <p><strong>Red belongs to the course.</strong> Ring 35 — the only ring on the committed track not
 * built from froglight — is orange wool, red concrete, red glazed terracotta and red nether bricks,
 * and a racer learns in one run that red is the finish. Every colour here, and every boss bar colour
 * the bar can take, is checked against that.
 *
 * <p><strong>A colour with two jobs has none.</strong> Every entry is distinct except the one pair
 * that is deliberately the same, and the test says which pair and why.
 */
class PaletteTest {

    /**
     * How red a colour is allowed to be before it starts competing with the finish line: red
     * clearly dominant over both other channels. Magenta {@code #FF3DA1} passes because its blue
     * channel is high; the design's original {@code #FF4D4D} would not, which is the point.
     */
    private static boolean readsAsRed(TextColor colour) {
        return colour.red() > 200 && colour.green() < 120 && colour.blue() < 120;
    }

    private static List<TextColor> everyColour() {
        return List.of(Palette.SPEED, Palette.OK, Palette.WARN, Palette.CAUTION, Palette.VALUE,
                Palette.DIM, Palette.PERSONAL_BEST, Palette.DIAMOND, Palette.GOLD, Palette.SILVER,
                Palette.BRONZE, Palette.FINISH);
    }

    @Test
    void nothingInThePaletteClaimsTheColourTheCourseMarksItsFinishIn() {
        assertThat(everyColour()).noneMatch(PaletteTest::readsAsRed);
    }

    @Test
    void noBandIsEverShownOnARedOrGreenBossBar() {
        for (MedalTier tier : MedalTier.values()) {
            assertThat(Palette.bossBarColour(tier))
                    .as("%s must not claim the finish line's colour, nor congratulate a loss", tier)
                    .isNotIn(BossBar.Color.RED, BossBar.Color.GREEN);
        }
    }

    @Test
    void everyBandThatIsAMedalGetsItsOwnBossBarColour() {
        assertThat(Palette.bossBarColour(MedalTier.DIAMOND)).isEqualTo(BossBar.Color.BLUE);
        assertThat(Palette.bossBarColour(MedalTier.GOLD)).isEqualTo(BossBar.Color.YELLOW);
        assertThat(Palette.bossBarColour(MedalTier.SILVER)).isEqualTo(BossBar.Color.WHITE);
        assertThat(Palette.bossBarColour(MedalTier.BRONZE)).isEqualTo(BossBar.Color.PINK);
        assertThat(Palette.bossBarColour(MedalTier.FINISH)).isEqualTo(BossBar.Color.PURPLE);

        List<BossBar.Color> medalColours = new ArrayList<>();
        for (MedalTier tier : new MedalTier[] {
                MedalTier.DIAMOND, MedalTier.GOLD, MedalTier.SILVER, MedalTier.BRONZE, MedalTier.FINISH }) {
            medalColours.add(Palette.bossBarColour(tier));
        }
        assertThat(medalColours).doesNotHaveDuplicates();
    }

    @Test
    void eachMedalIsWrittenInItsOwnColour() {
        assertThat(Palette.of(MedalTier.DIAMOND)).isEqualTo(Palette.DIAMOND);
        assertThat(Palette.of(MedalTier.GOLD)).isEqualTo(Palette.GOLD);
        assertThat(Palette.of(MedalTier.SILVER)).isEqualTo(Palette.SILVER);
        assertThat(Palette.of(MedalTier.BRONZE)).isEqualTo(Palette.BRONZE);
        assertThat(Palette.of(MedalTier.FINISH)).isEqualTo(Palette.FINISH);
        assertThat(Palette.of(MedalTier.DNF)).isEqualTo(Palette.WARN);
    }

    /**
     * One job per colour. {@code FINISH} and {@code DIM} are the same slate on purpose — a finish
     * outside every bracket <em>is</em> the absence of a medal, and saying so in the structural
     * colour is the statement — so the distinctness check covers everything else.
     */
    @Test
    void noTwoRolesShareAColourExceptTheOnePairThatMeansTheSameThing() {
        List<TextColor> distinct = new ArrayList<>(everyColour());
        distinct.remove(Palette.FINISH);

        assertThat(distinct).doesNotHaveDuplicates();
        assertThat(Palette.FINISH).isEqualTo(Palette.DIM);
    }

    @Test
    void everyPaletteTagResolvesAndColoursWhatItWraps() {
        for (String tag : List.of("c-speed", "c-ok", "c-warn", "c-caution", "c-value", "c-dim",
                "c-special", "m-diamond", "m-gold", "m-silver", "m-bronze", "m-finish")) {
            Component rendered = MiniMessage.miniMessage()
                    .deserialize("<%s>word".formatted(tag), Palette.tagResolver());

            assertThat(PlainTextComponentSerializer.plainText().serialize(rendered))
                    .as("<%s> must not be left in the text as an unknown tag", tag)
                    .isEqualTo("word");
            assertThat(rendered.color()).as("<%s> must colour what it wraps", tag).isNotNull();
        }
    }

    /** A tag nobody defined stays visible, which is how a typo in a bundle is found rather than hidden. */
    @Test
    void aTagThePaletteDoesNotDefineIsNotSilentlySwallowed() {
        Component rendered = MiniMessage.miniMessage()
                .deserialize("<c-nonexistent>word", Palette.tagResolver());

        assertThat(PlainTextComponentSerializer.plainText().serialize(rendered))
                .contains("c-nonexistent");
    }

    @Test
    void thePersonalBestColourIsReservedAndUsedByNothingElse() {
        assertThat(Arrays.stream(MedalTier.values()).map(Palette::of))
                .as("a colour spent on a medal is a colour that is no longer new")
                .doesNotContain(Palette.PERSONAL_BEST);
    }
}
