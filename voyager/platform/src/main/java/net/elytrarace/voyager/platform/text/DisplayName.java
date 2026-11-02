package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.text.exception.InvalidDisplayNameException;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A map or cup display name: MiniMessage, with the colour, decoration and reset tags only.
 *
 * <p>The name is parsed here and nowhere else, so a tag outside the set is refused where the file is read, and the name a
 * player sees is the one the file was checked for. A name may use any colour, red included. The visible length is what a
 * player reads, with the tags not counted, and it is at most {@link #VISIBLE_LIMIT} code points; a name is never cut.
 *
 * <p>A name that sets no colour of its own takes its key's hue from {@link NamePalette}. The hue is the base of the whole
 * name, so a coloured word keeps its colour and an uncoloured word takes the hue.
 */
@ApiStatus.Internal
public final class DisplayName {

    /** The most characters of visible text a display name may show. */
    public static final int VISIBLE_LIMIT = 40;

    /** A tag: an optional slash, a name that starts with a letter or {@code #}, and an optional argument list. */
    private static final Pattern TAG = Pattern.compile("<(/?)([#a-zA-Z][#a-zA-Z0-9_-]*)(?::[^>]*)?>");

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    /** Every named colour, the decoration names, {@code color}, {@code gradient}, {@code rainbow} and {@code reset}. */
    private static final Set<String> ALLOWED = allowedNames();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()))
            .build();

    private DisplayName() {
    }

    /**
     * Checks a display name as the catalogue states it.
     *
     * @param raw the name as written in the file
     * @throws InvalidDisplayNameException if a tag is outside the allowed set, or the visible text is longer than the limit
     */
    public static void check(String raw) {
        Matcher tag = TAG.matcher(raw);
        while (tag.find()) {
            String name = tag.group(2).toLowerCase(Locale.ROOT);
            if (!ALLOWED.contains(name) && !HEX.matcher(name).matches()) {
                throw InvalidDisplayNameException.disallowedTag(name);
            }
        }
        int visible = visibleLength(raw);
        if (visible > VISIBLE_LIMIT) {
            throw InvalidDisplayNameException.tooLong(visible, VISIBLE_LIMIT);
        }
    }

    /**
     * The number of code points a player reads of this name: its text with every tag removed.
     *
     * @param raw the name as written in the file
     * @return the visible length
     */
    @Contract(pure = true)
    public static int visibleLength(String raw) {
        String visible = MINI_MESSAGE.stripTags(raw);
        return visible.codePointCount(0, visible.length());
    }

    /**
     * The name a player reads: the parsed MiniMessage, with the key's palette hue as its colour wherever the name sets none.
     *
     * @param key the key of the map or cup, which chooses the hue
     * @param raw the name as written in the file, already checked by {@link #check}
     * @return the coloured name, ready to be an argument of a translation
     */
    @Contract(pure = true)
    public static Component component(Key key, String raw) {
        return MINI_MESSAGE.deserialize(raw).colorIfAbsent(NamePalette.hueFor(key));
    }

    /**
     * The name of a map that a stored record names by key, where the catalogue may no longer hold the map: the
     * map's display name when the catalogue has it, otherwise the plain key, which still tells the reader which
     * map the time was on.
     *
     * @param map    the catalogue's definition of the map, empty when the catalogue no longer holds it
     * @param mapKey the key the stored record names the map by
     * @return the coloured display name, or the plain key as text
     */
    @Contract(pure = true)
    public static Component orPlainKey(Optional<MapDefinition> map, String mapKey) {
        return map.<Component>map(found -> component(found.key(), found.displayName()))
                .orElseGet(() -> Component.text(mapKey));
    }

    private static Set<String> allowedNames() {
        Set<String> names = new HashSet<>(NamedTextColor.NAMES.keys());
        names.addAll(TextDecoration.NAMES.keys());
        names.add("color");
        names.add("gradient");
        names.add("rainbow");
        names.add("reset");
        return Set.copyOf(names);
    }
}
