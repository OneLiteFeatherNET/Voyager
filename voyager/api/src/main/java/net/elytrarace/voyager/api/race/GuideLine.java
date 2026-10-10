package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
 * <h2>Why the colour and the particle size are not in here</h2>
 *
 * <p>The tree being replaced carried {@code visibility}, {@code lookAhead}, {@code density},
 * {@code particleSpacing}, {@code particleSize} and an RGB colour in one record. Two of those six are
 * course data — how far ahead to look, and how dense the line is over gaps this long — and the rest
 * are presentation, identical on every course, with no reason for one map file to disagree with
 * another about them. They live as constants in the renderer, where changing them changes every
 * course at once, which is what "the line looks like this" means. {@code density} is not anywhere: it
 * was never read, in either tree.
 *
 * @param points the control points, in ascending order index order; empty is legal and means the line
 *     is the rings alone
 * @param lookAheadRings how many rings past the one the racer is heading for the line reaches
 * @param particleSpacing blocks between two particles along the line
 */
public record GuideLine(List<GuidePoint> points, int lookAheadRings, double particleSpacing) {

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
