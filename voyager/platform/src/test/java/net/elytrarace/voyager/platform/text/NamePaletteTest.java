package net.elytrarace.voyager.platform.text;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NamePaletteTest {

    /** The dark chat background the contrast ratios are measured against. */
    private static final TextColor CHAT_BACKGROUND = TextColor.color(0x1A1A1A);

    @Test
    void theSameKeyGetsTheSameHueEveryTime() {
        Key key = Key.key("elytrarace", "blue_and_red");

        assertThat(NamePalette.hueFor(key)).isEqualTo(NamePalette.hueFor(key));
    }

    @Test
    void theHueOfAKeyIsChosenByTheStableHashOfItsText() {
        Key key = Key.key("elytrarace", "blue_and_red");
        List<TextColor> hues = NamePalette.hues();

        assertThat(NamePalette.hueFor(key))
                .isEqualTo(hues.get(Math.floorMod(key.asString().hashCode(), hues.size())));
    }

    @Test
    void thePaletteIsTheSixDesignedHuesInOrder() {
        List<String> hex = NamePalette.hues().stream()
                .map(colour -> Integer.toHexString(colour.value()).toUpperCase())
                .toList();

        assertThat(hex).containsExactly("2EE6D6", "B47CFF", "FFD23F", "5CC8FF", "FF7AC6", "B8FF4D");
    }

    @Test
    void everyPaletteHueReachesAContrastRatioOfSixToOneOnTheChatBackground() {
        for (TextColor hue : NamePalette.hues()) {
            assertThat(contrast(hue, CHAT_BACKGROUND))
                    .as("contrast of #%06X on the chat background", hue.value())
                    .isGreaterThanOrEqualTo(6.0);
        }
    }

    /** The WCAG contrast ratio of two sRGB colours. */
    private static double contrast(TextColor first, TextColor second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(TextColor colour) {
        return 0.2126 * linear((colour.value() >> 16) & 0xFF)
                + 0.7152 * linear((colour.value() >> 8) & 0xFF)
                + 0.0722 * linear(colour.value() & 0xFF);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
