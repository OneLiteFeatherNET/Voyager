package net.elytrarace.voyager.platform.text;

import net.kyori.adventure.text.format.TextColor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Every text colour the race HUD uses reads at 4.5 to 1 or more on a dark backdrop.
 *
 * <p>The HUD is read in the middle of a turn at 33 blocks a second, over the sky and the course, so
 * a label that is merely visible on a white screen is not enough. The two backdrops are the darkest
 * the client draws behind the boss bar and the action bar. The ratio is WCAG 2.x relative luminance
 * with the usual {@code (L1 + 0.05) / (L2 + 0.05)}, computed here from the palette itself, so a
 * palette change that drops one colour below the line fails the build and names the colour.
 */
class PaletteContrastTest {

    private static final double MINIMUM_RATIO = 4.5;

    private static final TextColor BACKDROP_DARK = TextColor.color(0x101010);
    private static final TextColor BACKDROP_SLATE = TextColor.color(0x1E1E1E);

    /** The colours the boss bar title and the action bar draw their text in. */
    private static final Map<String, TextColor> HUD_TEXT = Map.of(
            "VALUE", Palette.VALUE,
            "OK", Palette.OK,
            "DIM", Palette.DIM,
            "DIAMOND", Palette.DIAMOND,
            "GOLD", Palette.GOLD,
            "SILVER", Palette.SILVER,
            "BRONZE", Palette.BRONZE,
            "WARN", Palette.WARN);

    static double contrast(TextColor foreground, TextColor backdrop) {
        double a = luminance(foreground);
        double b = luminance(backdrop);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(TextColor colour) {
        return 0.2126 * channel(colour.red()) + 0.7152 * channel(colour.green()) + 0.0722 * channel(colour.blue());
    }

    private static double channel(int value) {
        double srgb = value / 255.0;
        return srgb <= 0.03928 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    @Test
    void everyHudTextColourReadsAtLeast4Point5To1OnTheDarkBackdrop() {
        assertThat(HUD_TEXT)
                .allSatisfy((name, colour) -> assertThat(contrast(colour, BACKDROP_DARK))
                        .as("%s on #101010", name)
                        .isGreaterThanOrEqualTo(MINIMUM_RATIO));
    }

    @Test
    void everyHudTextColourReadsAtLeast4Point5To1OnTheSlateBackdrop() {
        assertThat(HUD_TEXT)
                .allSatisfy((name, colour) -> assertThat(contrast(colour, BACKDROP_SLATE))
                        .as("%s on #1E1E1E", name)
                        .isGreaterThanOrEqualTo(MINIMUM_RATIO));
    }

    /** The arithmetic is only as good as its check: black on white is the known maximum, 21 to 1. */
    @Test
    void theContrastFormulaAgreesWithTheKnownBlackOnWhiteRatio() {
        assertThat(contrast(TextColor.color(0x000000), TextColor.color(0xFFFFFF))).isCloseTo(21.0, within(0.01));
    }
}
