package net.elytrarace.voyager.api.race.exception;

/** Thrown when an author of a map or cup, or the list of authors, is not a valid record of who built it. */
public final class InvalidAuthorException extends RuntimeException {

    private InvalidAuthorException(String message) {
        super(message);
    }

    public static InvalidAuthorException missingUuid() {
        return new InvalidAuthorException("author uuid must not be null");
    }

    public static InvalidAuthorException badName(String name) {
        return new InvalidAuthorException(
                "author name must match [A-Za-z0-9_]{1,16}, was '%s'".formatted(name));
    }

    public static InvalidAuthorException tooMany(int count, int maximum) {
        return new InvalidAuthorException("authors must not list more than %s authors, was %s".formatted(maximum, count));
    }

    public static InvalidAuthorException repeated(String uuid) {
        return new InvalidAuthorException("authors must not list the uuid %s twice".formatted(uuid));
    }
}
