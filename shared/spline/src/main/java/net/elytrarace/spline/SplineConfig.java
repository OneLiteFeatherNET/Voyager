package net.elytrarace.spline;

/**
 * Configuration for the spline ideal line, adjustable per game or per player.
 *
 * @param visibility      how much of the spline to show
 * @param lookAhead       number of portals ahead to show (only for {@link SplineVisibility#PARTIAL})
 * @param density         interpolation density multiplier (1.0 = default, higher = smoother)
 * @param particleSpacing blocks between particles (smaller = denser line, more visible on long stretches)
 * @param particleSize    particle size (0.5-4.0, bigger = more visible from distance)
 * @param colorR          particle color red component (0-255)
 * @param colorG          particle color green component (0-255)
 * @param colorB          particle color blue component (0-255)
 */
public record SplineConfig(
        SplineVisibility visibility,
        int lookAhead,
        double density,
        double particleSpacing,
        float particleSize,
        int colorR,
        int colorG,
        int colorB
) {

    /** Easy: full spline, clearly visible, dense particles. */
    public static final SplineConfig EASY = new SplineConfig(
            SplineVisibility.FULL, 0, 1.0, 0.8, 1.5f, 50, 255, 50); // green, big, dense

    /** Medium: only next 5 portals, moderate visibility. */
    public static final SplineConfig MEDIUM = new SplineConfig(
            SplineVisibility.PARTIAL, 5, 1.0, 1.0, 1.0f, 255, 200, 0); // yellow

    /** Hard: no spline visible. */
    public static final SplineConfig HARD = new SplineConfig(
            SplineVisibility.HIDDEN, 0, 1.0, 1.5, 0.5f, 0, 0, 0);

    /** Builder preview: full spline, high density, bright orange. */
    public static final SplineConfig BUILDER = new SplineConfig(
            SplineVisibility.FULL, 0, 1.5, 0.5, 2.0f, 255, 140, 0); // orange, very big, very dense

    /**
     * Creates a copy with different particle settings.
     */
    public SplineConfig withParticles(double spacing, float size, int r, int g, int b) {
        return new SplineConfig(visibility, lookAhead, density, spacing, size, r, g, b);
    }

    /**
     * Creates a copy with different visibility.
     */
    public SplineConfig withVisibility(SplineVisibility visibility) {
        return new SplineConfig(visibility, lookAhead, density, particleSpacing, particleSize, colorR, colorG, colorB);
    }
}
