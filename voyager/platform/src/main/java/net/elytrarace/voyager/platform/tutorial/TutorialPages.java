package net.elytrarace.voyager.platform.tutorial;

import net.kyori.adventure.key.Key;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.OptionalInt;

/**
 * The tutorial's page model: five pages in a fixed order, and the keys the dialog buttons send back. No Pica type and no
 * Minestom type, so the order and the buttons are testable without a server.
 *
 * <p>The pages are how to play, rockets, the BOOST and SLOW rings, checkpoints and resets, and medals. Each page's text is a
 * translation key in {@code Messages}, in both bundles.
 */
@ApiStatus.Internal
@NotNullByDefault
public final class TutorialPages {

    /** How many pages the tutorial has. */
    public static final int COUNT = 6;

    /** The page a tutorial opens on, every time it opens. Nothing records where a racer left it. */
    public static final int FIRST_PAGE = 1;

    /** The key the Close button sends back. It names no page. */
    public static final Key CLOSE_KEY = Key.key("voyager", "tutorial/close");

    private static final String PAGE_KEY_PATTERN = "tutorial/page/%d";

    private TutorialPages() {
    }

    /**
     * The key a page is opened by.
     *
     * @param page the page, counted from one
     */
    @Contract(pure = true)
    public static Key keyOf(int page) {
        return Key.key("voyager", PAGE_KEY_PATTERN.formatted(page));
    }

    /**
     * The page a key names.
     *
     * @param key the key a button sent back
     * @return the page, counted from one, or empty for the close key and for any key of another dialog
     */
    public static OptionalInt indexOf(Key key) {
        if (!"voyager".equals(key.namespace())) {
            return OptionalInt.empty();
        }
        for (int page = 1; page <= COUNT; page++) {
            if (keyOf(page).equals(key)) {
                return OptionalInt.of(page);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Whether a page has a Back button: every page but the first.
     *
     * @param page the page, counted from one
     */
    @Contract(pure = true)
    public static boolean hasBack(int page) {
        return page > FIRST_PAGE;
    }

    /**
     * Whether a page has a Next button: every page but the last.
     *
     * @param page the page, counted from one
     */
    @Contract(pure = true)
    public static boolean hasNext(int page) {
        return page < COUNT;
    }
}
