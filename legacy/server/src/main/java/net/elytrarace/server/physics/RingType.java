package net.elytrarace.server.physics;

/**
 * Defines the different ring types available in an elytra race course.
 * Each type applies a different effect when a player passes through it.
 */
public enum RingType {
    /** Normal ring that awards points. */
    STANDARD,
    /** Awards points and gives a speed boost (velocity * 1.5). */
    BOOST,
    /** Mandatory checkpoint that must be passed to complete the race. */
    CHECKPOINT,
    /** Awards points but reduces speed (velocity * 0.5). */
    SLOW,
    /** Optional off-route ring that awards extra points. */
    BONUS
}
