package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The racing line a course shows its racers: the control points that bend it, how far ahead of the
 * racer it reaches, and how densely it is drawn.
 *
 * <p>The line itself is not stored. It is the rings and {@link #points()} merged in order index
 * order and run through a spline, which is a derivation from data that is already in the map file —
 * storing the result as well would be a second copy to keep in step with the first.
 *
 * <h2>Why {@code lookAheadRings} is in the map file and why it is the field that matters</h2>
 *
 * <p>The committed course is 1588 blocks long, climbs 373 of them and doubles back over itself. Drawn
 * all at once it is a ball of string: every strand is visible and none of them says which one is
 * next. Drawn from the ring the racer is heading for forward, it says exactly one thing, which is
 * where to go next. So the number of rings the line reaches ahead is the difference between a guide
 * and a decoration, and it is per course rather than per server because how far ahead is useful
 * depends on how far apart that course's rings are.
 *
 * <h2>Why the particle and the colour are in the map file</h2>
 *
 * <p>The tree being replaced carried {@code visibility}, {@code lookAhead}, {@code density},
 * {@code particleSpacing}, {@code particleSize} and an RGB colour in one record, and the rebuild held
 * the colour and particle as renderer constants. ADR-0030 changes that: the owner wants per-course
 * choice, so {@code particle} and {@code dustRgb} are course data with one default every course shares.
 * Two courses may now draw differently, and a racer learns the line's look per course. The packet cost
 * of a choice is stated in the change's design, and the server enforces its budget rather than the map.
 *
 * <h2>Mode, particle and colour</h2>
 *
 * <p>{@code mode} is {@link GuideLineMode#FULL} by default: the line from the ring the racer is heading
 * for to the last ring. {@link GuideLineMode#WINDOW} keeps the old look-ahead window. {@code particle}
 * is {@link GuideParticle#END_ROD} by default; {@code dustRgb} is the colour of a
 * {@link GuideParticle#DUST} line and is refused on any other particle. A {@code null} {@code dustRgb}
 * means the map named no colour, and {@link #dustRgbOrDefault()} gives the amber every course shares.
 *
 * @param points the control points, in ascending order index order; empty is legal and means the line
 *     is the rings alone
 * @param lookAheadRings how many rings past the one the racer is heading for the line reaches; used by
 *     {@link GuideLineMode#WINDOW} only
 * @param particleSpacing blocks between two particles along the line
 * @param mode how much of the line a racer is shown
 * @param particle the particle the line is drawn with
 * @param dustRgb the colour of a dust line, as {@code 0xRRGGBB}; {@code null} when the map named none
 */
public record GuideLine(List<GuidePoint> points, int lookAheadRings, double particleSpacing,
                        GuideLineMode mode, GuideParticle particle, @Nullable Integer dustRgb) {

    /** The colour a dust line takes when the map names none: the amber the line has always used. */
    public static final int DEFAULT_DUST_RGB = 0xFFC800;

    private static final Pattern DUST_COLOUR = Pattern.compile("#[0-9A-Fa-f]{6}");

    /**
     * A line with the default style: {@link GuideLineMode#FULL}, {@link GuideParticle#END_ROD} and no colour.
     *
     * @param points the control points
     * @param lookAheadRings how many rings past the racer's ring the line reaches in {@code WINDOW} mode
     * @param particleSpacing blocks between two particles along the line
     */
    public GuideLine(List<GuidePoint> points, int lookAheadRings, double particleSpacing) {
        this(points, lookAheadRings, particleSpacing, GuideLineMode.FULL, GuideParticle.END_ROD, null);
    }

    /**
     * The colour a dust line is drawn in: the map's own colour, or the default when it named none.
     *
     * @return the colour as {@code 0xRRGGBB}
     */
    public int dustRgbOrDefault() {
        return dustRgb == null ? DEFAULT_DUST_RGB : dustRgb;
    }

    /**
     * Reads a colour written as {@code #RRGGBB}.
     *
     * @param text the colour as written in the map file
     * @return the colour as {@code 0xRRGGBB}
     * @throws InvalidGuideLineException if the text is not {@code #} followed by six hexadecimal digits
     */
    public static int parseDustColor(String text) {
        if (!DUST_COLOUR.matcher(text).matches()) {
            throw InvalidGuideLineException.badDustColor(text);
        }
        return Integer.parseInt(text.substring(1), 16);
    }

    /**
     * The closest two particles may be drawn, in blocks.
     *
     * <p>A floor rather than a matter of taste: the spacing divides a length, so halving it doubles
     * the packets a racer is sent every time the line is refreshed. At a quarter of a block a
     * two-ring stretch of the committed course is already around eight hundred particles for one
     * player, and a file that asked for a hundredth of a block would ask the server to send twenty
     * thousand — a typo in committed data turning into a packet flood on a live server, which is
     * exactly the class of failure a value object is meant to refuse rather than forward.
     */
    public static final double MINIMUM_PARTICLE_SPACING = 0.25;

    public GuideLine {
        if (lookAheadRings < 1) {
            throw InvalidGuideLineException.nonPositiveLookAhead(lookAheadRings);
        }
        if (!Double.isFinite(particleSpacing) || particleSpacing < MINIMUM_PARTICLE_SPACING) {
            throw InvalidGuideLineException.particleSpacingTooFine(particleSpacing);
        }
        if (dustRgb != null && particle != GuideParticle.DUST) {
            throw InvalidGuideLineException.dustColorWithoutDust(particle);
        }
        // Sorted here rather than refused, unlike the rings above it in MapDefinition. A ring's index
        // has to match its position in the list because a progress tracker reads the next ring by
        // position, so a mismatch means a ring is missing; a guide's order index carries its order
        // whole, so list order adds nothing and sorting loses nothing. What is genuinely ambiguous —
        // two guides claiming the same slot, where no rule says which the line reaches first — is
        // refused instead.
        List<GuidePoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingInt(GuidePoint::orderIndex));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).orderIndex() == sorted.get(i - 1).orderIndex()) {
                throw InvalidGuideLineException.duplicateOrderIndex(sorted.get(i).orderIndex());
            }
        }
        points = List.copyOf(sorted);
    }
}
