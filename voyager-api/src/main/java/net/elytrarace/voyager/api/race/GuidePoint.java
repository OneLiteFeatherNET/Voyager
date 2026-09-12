package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;

/**
 * One control point that bends the racing line between two rings.
 *
 * <p>A guide point is not a ring: nothing is scored at it, nothing has to be flown through it, and a
 * course with none of them is a legal course. It exists because a line drawn straight from ring to
 * ring cuts through terrain, and a racer following that line flies into a wall.
 *
 * <h2>Why an order index rather than a position in a list</h2>
 *
 * <p>{@link #orderIndex()} places the guide on one axis shared with the rings: <strong>ring
 * {@code i} occupies {@code i * }{@link #RING_ORDER_STRIDE}</strong>, and a guide sits at whatever
 * value falls between the two rings it bends the line between. The line is then the rings and the
 * guides merged in order index order, which is one sort rather than a per-gap bookkeeping structure.
 *
 * <p>The stride is what makes the scheme editable. With rings a hundred apart, a designer can insert
 * a guide between two existing ones — 2450 and 2475 both sit between rings 24 and 25 in the committed
 * course — without renumbering anything. That is also the case any code reading this must not get
 * wrong: <strong>more than one guide per gap is legal</strong>, and an implementation that assumed
 * one would silently drop the other and put the line back through the wall the second one was placed
 * to avoid.
 *
 * @param orderIndex where the guide sits on the shared order axis; never on a ring's own slot
 * @param position where the line is pulled to, in blocks
 */
public record GuidePoint(int orderIndex, Vec3 position) {

    /**
     * The distance between two rings' slots on the order axis: ring {@code i} is at {@code i * 100}.
     *
     * <p>A hundred, and inherited from the data rather than chosen here — the old tree's setup wizard
     * wrote guide points on this axis and {@code guides.json} is full of them. Any gapped stride would
     * do; changing this one would renumber every guide in every committed map file.
     */
    public static final int RING_ORDER_STRIDE = 100;

    public GuidePoint {
        if (orderIndex % RING_ORDER_STRIDE == 0) {
            throw InvalidGuideLineException.onARingsOwnSlot(orderIndex);
        }
    }

    /**
     * The index of the ring this guide follows — the first of the two rings it bends the line
     * between.
     *
     * <p>{@code Math.floorDiv} rather than {@code /}: an order index below zero sits before the first
     * ring, and integer division would round it towards zero and report ring 0, which is a guide
     * inside the course rather than in front of it. The map is what refuses that, and it can only do
     * so if this answers {@code -1}.
     */
    public int afterRing() {
        return Math.floorDiv(orderIndex, RING_ORDER_STRIDE);
    }
}
