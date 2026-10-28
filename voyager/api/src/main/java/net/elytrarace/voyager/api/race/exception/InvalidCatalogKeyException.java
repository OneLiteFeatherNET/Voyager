package net.elytrarace.voyager.api.race.exception;

/** Thrown when a map or cup identifier is not a key in the {@code elytrarace} namespace with a valid value. */
public final class InvalidCatalogKeyException extends RuntimeException {

    private InvalidCatalogKeyException(String message) {
        super(message);
    }

    public static InvalidCatalogKeyException badValue(String value) {
        return new InvalidCatalogKeyException(
                "key value must match [a-z0-9][a-z0-9_-]{0,31}, was '%s'".formatted(value));
    }

    public static InvalidCatalogKeyException foreignNamespace(String key) {
        return new InvalidCatalogKeyException("key '%s' must be in the namespace elytrarace".formatted(key));
    }
}
