package net.elytrarace.api.database.service;

/**
 * Thrown when {@link DatabaseService#init()} cannot build a working
 * {@code SessionFactory} — typically because the database is unreachable,
 * credentials are wrong, or the schema action fails to validate/update.
 */
public final class DatabaseInitializationException extends RuntimeException {

    public DatabaseInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseInitializationException(String message) {
        super(message);
    }
}
