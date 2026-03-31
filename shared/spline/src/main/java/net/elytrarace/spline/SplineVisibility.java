package net.elytrarace.spline;

/**
 * Controls how the spline (ideal racing line) is displayed to a player.
 * Used for difficulty levels — harder modes show less guidance.
 */
public enum SplineVisibility {

    /**
     * Full spline visible at all times. For beginners and easy mode.
     */
    FULL,

    /**
     * Only the spline segment for the next N portals is visible.
     * The path behind the player and far ahead fades out.
     */
    PARTIAL,

    /**
     * No spline visible. Player must learn the route from memory.
     * For hard mode and competitive play.
     */
    HIDDEN
}
