package net.elytrarace.voyager.race.com.exception;

/** Thrown when a computer racer's tuning is outside its range: a speed above the reference, a negative amplitude or reaction delay, or a chance outside zero to one. */
public final class InvalidComTuningException extends RuntimeException {

    private InvalidComTuningException(String message) {
        super(message);
    }

    public static InvalidComTuningException speedFactorOutOfRange(double factor) {
        return new InvalidComTuningException(
                "speed factor must be above zero and at most one, was %s".formatted(factor));
    }

    public static InvalidComTuningException personalSpeedOutOfRange(double personal) {
        return new InvalidComTuningException(
                "personal speed must lie within two per cent of one, was %s".formatted(personal));
    }

    public static InvalidComTuningException negativeAmplitude(double amplitude) {
        return new InvalidComTuningException(
                "line offset amplitude must not be negative, was %s".formatted(amplitude));
    }

    public static InvalidComTuningException nonPositiveWobblePeriod(double period) {
        return new InvalidComTuningException(
                "wobble period must be positive, was %s".formatted(period));
    }

    public static InvalidComTuningException missChanceOutOfRange(double chance) {
        return new InvalidComTuningException(
                "miss chance must lie between zero and one, was %s".formatted(chance));
    }

    public static InvalidComTuningException negativeReactionDelay(int ticks) {
        return new InvalidComTuningException(
                "boost reaction delay must not be negative, was %s ticks".formatted(ticks));
    }
}
