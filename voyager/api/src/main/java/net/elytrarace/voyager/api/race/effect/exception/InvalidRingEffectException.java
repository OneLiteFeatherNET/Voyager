package net.elytrarace.voyager.api.race.effect.exception;

/** Thrown when a {@code RingEffectTuning} is built with a factor that is negative or not finite. */
public final class InvalidRingEffectException extends RuntimeException {

    private InvalidRingEffectException(String message) {
        super(message);
    }

    /**
     * @param field the name of the map-file field the factor came from, so the message points at the file
     * @param factor the refused value
     */
    public static InvalidRingEffectException factor(String field, double factor) {
        return new InvalidRingEffectException(
                "ring effect field %s must be finite and zero or more, was %s".formatted(field, factor));
    }
}
