package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.race.com.exception.InvalidComTuningException;

import java.util.Random;

/**
 * One computer racer's tuning: how fast it aims to go, how far it wanders off the line, how often it misses a
 * ring and how long a boost takes to reach it.
 *
 * <p>The table is the design's (D3), and the speeds are bounded by D14. A difficulty never makes a computer racer
 * faster than the reference speed {@link #REFERENCE_SPEED}, so HARD is the reference racer and not a cheat. The
 * personal speed is a seeded variation of plus or minus two per cent, so two racers at the same difficulty differ.
 *
 * @param speedFactor share of the reference speed this difficulty aims for, above zero and at most one
 * @param personalSpeed seeded variation of the speed, within two per cent of one
 * @param lineOffsetAmplitude how far the racer wanders off the line, in blocks, never negative
 * @param wobblePeriod length of one wander along the course, in blocks, above zero
 * @param missChance chance a ring is missed once, between zero and one
 * @param boostReactionDelayTicks ticks before a boost ring's speed effect applies, never negative
 */
public record ComTuning(
        double speedFactor,
        double personalSpeed,
        double lineOffsetAmplitude,
        double wobblePeriod,
        double missChance,
        int boostReactionDelayTicks) {

    /** The reference speed of the physics reference, in blocks per tick: the rocket's terminal speed. */
    public static final double REFERENCE_SPEED = 1.7;

    /** The slowest a computer racer is allowed to move while it is on the course, in blocks per tick. */
    public static final double FLOOR_SPEED = 0.70;

    /** The largest seeded personal variation of the speed, as a fraction of one. */
    static final double PERSONAL_VARIATION = 0.02;

    public ComTuning {
        if (!(speedFactor > 0.0 && speedFactor <= 1.0)) {
            throw InvalidComTuningException.speedFactorOutOfRange(speedFactor);
        }
        if (!(personalSpeed >= 1.0 - PERSONAL_VARIATION && personalSpeed <= 1.0 + PERSONAL_VARIATION)) {
            throw InvalidComTuningException.personalSpeedOutOfRange(personalSpeed);
        }
        if (!(lineOffsetAmplitude >= 0.0)) {
            throw InvalidComTuningException.negativeAmplitude(lineOffsetAmplitude);
        }
        if (!(wobblePeriod > 0.0)) {
            throw InvalidComTuningException.nonPositiveWobblePeriod(wobblePeriod);
        }
        if (!(missChance >= 0.0 && missChance <= 1.0)) {
            throw InvalidComTuningException.missChanceOutOfRange(missChance);
        }
        if (boostReactionDelayTicks < 0) {
            throw InvalidComTuningException.negativeReactionDelay(boostReactionDelayTicks);
        }
    }

    /**
     * The tuning of one difficulty, with the personal speed drawn from {@code seed}.
     *
     * @param difficulty how hard the racer plays
     * @param seed seeds the personal speed; the same seed gives the same racer
     * @return the tuning
     */
    public static ComTuning of(ComDifficulty difficulty, long seed) {
        double personal = 1.0 + (new Random(seed).nextDouble() * 2.0 - 1.0) * PERSONAL_VARIATION;
        return switch (difficulty) {
            case EASY -> new ComTuning(0.85, personal, 1.2, 40.0, 0.08, 10);
            case MEDIUM -> new ComTuning(0.93, personal, 0.6, 60.0, 0.02, 4);
            case HARD -> new ComTuning(1.00, personal, 0.2, 80.0, 0.00, 0);
        };
    }

    /**
     * The speed factor after the personal variation, capped at one so that no racer is faster than the reference.
     *
     * @return a value above zero and at most one
     */
    public double effectiveSpeedFactor() {
        return Math.min(1.0, speedFactor * personalSpeed);
    }

    /**
     * The speed this racer aims for on a straight stretch, in blocks per tick.
     *
     * @return the effective speed factor times the reference speed
     */
    public double targetSpeed() {
        return effectiveSpeedFactor() * REFERENCE_SPEED;
    }
}
