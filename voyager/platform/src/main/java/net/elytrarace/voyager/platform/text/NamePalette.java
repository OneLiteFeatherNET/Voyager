package net.elytrarace.voyager.platform.text;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * The six bright hues a map or cup name takes when its file gives it no colour of its own.
 *
 * <p>The hue is chosen by a stable hash of the key, so the same key is the same hue on every server and in every chat. Each
 * hue reaches a contrast ratio of at least 6:1 on the dark chat background {@code #1A1A1A}, which the test measures. The
 * order of the list is part of the contract: a change of order changes every shipped name's colour.
 */
@ApiStatus.Internal
@NotNullByDefault
public final class NamePalette {

    /** Turquoise, violet, gold, sky blue, pink and lime, in that order. */
    private static final List<TextColor> HUES = List.of(
            TextColor.color(0x2EE6D6),
            TextColor.color(0xB47CFF),
            TextColor.color(0xFFD23F),
            TextColor.color(0x5CC8FF),
            TextColor.color(0xFF7AC6),
            TextColor.color(0xB8FF4D));

    private NamePalette() {
    }

    /** The six hues, in the order the hash indexes them. */
    public static List<TextColor> hues() {
        return HUES;
    }

    /**
     * The hue of a key's name when the name sets no colour: {@code hues[floorMod(hashCode, 6)]} of the key's text, where
     * {@link String#hashCode()} is fixed by the Java specification.
     */
    @Contract(pure = true)
    public static TextColor hueFor(Key key) {
        return HUES.get(Math.floorMod(key.asString().hashCode(), HUES.size()));
    }
}
