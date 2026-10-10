package net.elytrarace.voyager.api.race.exception;

/** Thrown when a {@code CupDefinition} is constructed with a value that would make it unplayable. */
public final class InvalidCupException extends RuntimeException {

    private InvalidCupException(String message) {
        super(message);
    }

    public static InvalidCupException blankName(String name) {
        return new InvalidCupException("cup name must not be blank, was '%s'".formatted(name));
    }

    public static InvalidCupException emptyMapNames() {
        return new InvalidCupException("cup map names must not be null or empty");
    }

    public static InvalidCupException missingMode() {
        return new InvalidCupException("cup mode must not be null");
    }
}
