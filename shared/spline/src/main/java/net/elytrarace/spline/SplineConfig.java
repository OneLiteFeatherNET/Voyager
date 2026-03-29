package net.elytrarace.spline;

/**
 * Configuration for the spline ideal line, adjustable per game or per player.
 *
 * @param visibility  how much of the spline to show
 * @param lookAhead   number of portals ahead to show (only for {@link SplineVisibility#PARTIAL})
 * @param density     interpolation density multiplier (1.0 = default, higher = smoother)
 */
public record SplineConfig(
        SplineVisibility visibility,
        int lookAhead,
        double density
) {

    /** Easy: full spline, always visible. */
    public static final SplineConfig EASY = new SplineConfig(SplineVisibility.FULL, 0, 1.0);

    /** Medium: only next 5 portals visible. */
    public static final SplineConfig MEDIUM = new SplineConfig(SplineVisibility.PARTIAL, 5, 1.0);

    /** Hard: no spline visible. */
    public static final SplineConfig HARD = new SplineConfig(SplineVisibility.HIDDEN, 0, 1.0);

    /** Builder preview: full spline with higher density for visual clarity. */
    public static final SplineConfig BUILDER = new SplineConfig(SplineVisibility.FULL, 0, 1.5);
}
