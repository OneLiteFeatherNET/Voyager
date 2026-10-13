package net.elytrarace.voyager.race.com.exception;

/** Thrown when the computer racer count is outside its range, or when the human count on the grid is negative. */
public final class InvalidComCountException extends RuntimeException {

    private InvalidComCountException(String message) {
        super(message);
    }

    public static InvalidComCountException outOfRange(int count, int maximum) {
        return new InvalidComCountException(
                "computer racer count must lie between 0 and %s, was %s".formatted(maximum, count));
    }

    public static InvalidComCountException negativeHumans(int humans) {
        return new InvalidComCountException(
                "human racers on the grid must not be negative, was %s".formatted(humans));
    }
}
