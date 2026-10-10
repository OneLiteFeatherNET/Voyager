package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.RingType;

import java.util.List;

/**
 * The values a new ring and a new map take when the builder does not state them. The radius is the radius of every
 * ring of the sample map ({@code sqrt(13)}); the reference time, boost and guide line are the sample's provisional
 * seeds, which the owner has not confirmed (open question O5).
 */
public final class RingDefaults {

    /** The radius of a placed ring: {@code sqrt(13)}, as on every ring of the sample map. */
    public static final double RADIUS = Math.sqrt(13);
    /** The points a placed ring scores. */
    public static final int POINTS = 10;
    /** The type of a placed ring. */
    public static final RingType TYPE = RingType.STANDARD;
    /** How far along the look ray, in blocks, a left-click reaches a ring. */
    public static final double REACH_BLOCKS = 32.0;
    /** The provisional reference time of a new map, in seconds. */
    public static final double REFERENCE_TIME_SECONDS = 60.0;
    /** The provisional boost tuning of a new map. */
    public static final BoostConfig BOOST = new BoostConfig(30, 40);
    /** The provisional racing line of a new map: no points, two rings of look-ahead, one block between particles. */
    public static final GuideLine GUIDE_LINE = new GuideLine(List.of(), 2, 1.0);

    private RingDefaults() {
    }
}
