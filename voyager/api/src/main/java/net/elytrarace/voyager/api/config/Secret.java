package net.elytrarace.voyager.api.config;

/**
 * A value that must not reach a log, a chat message or a report: the database password. Its {@link #toString()}
 * prints {@code Secret[masked]}.
 *
 * @param value the secret itself, never blank
 */
public record Secret(String value) {

    public Secret {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
    }

    /**
     * The secret itself. Only the {@code persistence} modules and the composition root may call this; no
     * other class may print it, log it or pass it on.
     *
     * @return the secret text
     */
    @Override
    public String value() {
        return value;
    }

    /**
     * Masks the secret, so that printing a configuration never prints the password.
     *
     * @return {@code Secret[masked]}
     */
    @Override
    public String toString() {
        return "Secret[masked]";
    }
}
