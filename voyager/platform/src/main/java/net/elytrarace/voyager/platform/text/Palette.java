package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.api.race.MedalTier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Every colour the game speaks in, each with exactly one job.
 *
 * <h2>Red is taken, and not by us</h2>
 *
 * <p>The course marks its own finish in red. Of the 35 rings on the committed track, 34 are froglight
 * — verdant and pearlescent in runs of two to six — and ring 35 alone is orange wool, red concrete,
 * red glazed terracotta and red nether bricks. A racer learns in one run that red is the end of the
 * course, in the world, at 33 blocks a second. <strong>So nothing on any screen surface may claim
 * red.</strong> That is why the "act now" role below is magenta rather than the obvious choice, and
 * why {@link #BOSS_BAR_COLOUR} is never {@link BossBar.Color#RED}.
 *
 * <p>(What the two froglight runs <em>mean</em> is unknown. They are sections, not an alternation, and
 * nothing in the map data distinguishes them — every ring on the course is {@code STANDARD} and worth
 * ten points. Nothing here is keyed to them, and nothing should be until somebody who built the course
 * says what they are.)
 *
 * <h2>One accent per line</h2>
 *
 * <p>A line is {@link #DIM} structure plus at most one accent. A colour with two jobs has none, and a
 * line with three accents has no emphasis — by the tenth one the player has stopped reading colour at
 * all.
 *
 * <h2>The palette lives here, not in the properties file</h2>
 *
 * <p>{@link #tagResolver()} is what makes {@code <c-speed>} mean something inside a translation value.
 * A hex code repeated across forty properties lines in three languages is a palette that has already
 * drifted, and a translator must be able to translate a line without being able to recolour it.
 *
 * <p>The colour, wording and channel rules that these lines follow are in
 * {@code docs/reference/message-style-guide.md}.
 */
@ApiStatus.Internal
public abstract class Palette {

    /** Speed, boost, and the game's own voice. Cyan. */
    public static final TextColor SPEED = TextColor.color(0x00C2FF);

    /** Something went right for you: a ring passed, a finish line, a record beaten. Green. */
    public static final TextColor OK = TextColor.color(0x5BFF8F);

    /**
     * Something is wrong and you have to act: a missed ring, a DNF. Magenta.
     *
     * <p>Magenta and not red, and this is the one entry in the table that is a compromise rather than
     * a choice. Red is the obvious colour for an alarm and the course has already spent it on the
     * finish; a second meaning for it would teach a racer to read the one thing they must not
     * misread. Magenta is the nearest colour that still reads as alarm, is nowhere on the track, and
     * survives deuteranopia — which red never did against {@link #OK}, and which is why a missed ring
     * will be told apart from a passed one by sound first, symbol second and colour third.
     */
    public static final TextColor WARN = TextColor.color(0xFF3DA1);

    /** Caution, waiting, not yet: the countdown digits. Amber. */
    public static final TextColor CAUTION = TextColor.color(0xFFB020);

    /** A number or a name the player cares about: the ring counter, a score, a username. White. */
    public static final TextColor VALUE = TextColor.color(0xFFFFFF);

    /** Labels, units, separators — everything structural. Slate. */
    public static final TextColor DIM = TextColor.color(0x8A93A0);

    /**
     * Reserved for a personal best, and deliberately unused today.
     *
     * <p>{@code voyager-persistence} does not exist, so there is no personal best to celebrate.
     * Reserving the colour now means that when one arrives it lands on a colour the player has never
     * seen — which is most of what will make the moment read as special. A colour spent early is a
     * colour spent.
     */
    public static final TextColor PERSONAL_BEST = TextColor.color(0xC77DFF);

    /** Diamond. */
    public static final TextColor DIAMOND = TextColor.color(0x66E0E0);

    /** Gold. */
    public static final TextColor GOLD = TextColor.color(0xFFCC33);

    /** Silver. */
    public static final TextColor SILVER = TextColor.color(0xCFD6DE);

    /** Bronze. */
    public static final TextColor BRONZE = TextColor.color(0xC87A3E);

    /** A finish worth no medal: the same slate as {@link #DIM}, because that is what it says. */
    public static final TextColor FINISH = DIM;

    /**
     * The tag names this palette answers to inside a translation value, one per role above.
     *
     * <p>Built once: a {@link TagResolver} over fixed colours has nothing to recompute, and this is
     * consulted on every line the server sends.
     */
    private static final TagResolver TAGS = TagResolver.resolver(
            colour("c-speed", SPEED),
            colour("c-ok", OK),
            colour("c-warn", WARN),
            colour("c-caution", CAUTION),
            colour("c-value", VALUE),
            colour("c-dim", DIM),
            colour("c-special", PERSONAL_BEST),
            colour("m-diamond", DIAMOND),
            colour("m-gold", GOLD),
            colour("m-silver", SILVER),
            colour("m-bronze", BRONZE),
            colour("m-finish", FINISH));

    private Palette() {
    }

    /** The palette as MiniMessage tags, for a translation value to colour itself with. */
    @Contract(pure = true)
    public static TagResolver tagResolver() {
        return TAGS;
    }

    /** The colour a {@link MedalTier} is written in. */
    @Contract(pure = true)
    public static TextColor of(MedalTier tier) {
        return switch (tier) {
            case DIAMOND -> DIAMOND;
            case GOLD -> GOLD;
            case SILVER -> SILVER;
            case BRONZE -> BRONZE;
            case FINISH -> FINISH;
            case DNF -> WARN;
        };
    }

    /**
     * The boss bar's colour: one fixed colour for every medal band.
     *
     * <p>The bar is the ring progress, and the medal is named by the title. A bar that took the band's
     * colour competed with the title that named the same band, so the colour no longer follows the
     * band. The bar is never {@link BossBar.Color#RED}, which the course spent on its finish line.
     */
    public static final BossBar.Color BOSS_BAR_COLOUR = BossBar.Color.GREEN;

    private static TagResolver colour(String name, TextColor colour) {
        return TagResolver.resolver(name, Tag.styling(colour));
    }
}
