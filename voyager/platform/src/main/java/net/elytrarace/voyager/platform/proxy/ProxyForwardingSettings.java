package net.elytrarace.voyager.platform.proxy;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Whether the server accepts player identity from a Velocity proxy, and the secret that signs it.
 *
 * <p>The secret is read from the environment variable {@value #ENVIRONMENT_NAME}, or else from the system property
 * {@value #PROPERTY_NAME}. A set but blank value is refused rather than treated as absent, so a mistyped secret can never
 * quietly turn signature checking off. With neither set, the server runs offline and the composition root logs a WARN.
 *
 * @param secret the shared secret, or {@code null} when the server runs offline
 */
public record ProxyForwardingSettings(@Nullable String secret) {

    /** The environment variable that names the secret. It wins over the property. */
    public static final String ENVIRONMENT_NAME = "VOYAGER_VELOCITY_SECRET";

    /** The system property that names the secret when the environment variable is absent. */
    public static final String PROPERTY_NAME = "voyager.velocity.secret";

    public ProxyForwardingSettings {
        if (secret != null && secret.isBlank()) {
            throw new IllegalArgumentException("the Velocity secret must not be blank");
        }
    }

    /**
     * Reads the settings from the two sources. The environment is read first, and the property only when the variable is
     * absent.
     *
     * @param environment the process environment, by variable name
     * @param properties  the system properties, by name
     * @return the settings; offline when neither source is set
     * @throws IllegalArgumentException when the source that is set holds only whitespace
     */
    public static ProxyForwardingSettings from(Map<String, String> environment, Map<String, String> properties) {
        String fromEnvironment = environment.get(ENVIRONMENT_NAME);
        if (fromEnvironment != null) {
            return fromSource(fromEnvironment, ENVIRONMENT_NAME);
        }
        String fromProperty = properties.get(PROPERTY_NAME);
        if (fromProperty != null) {
            return fromSource(fromProperty, PROPERTY_NAME);
        }
        return new ProxyForwardingSettings(null);
    }

    /**
     * Whether the server forwards player identity, that is, whether a secret is set.
     *
     * @return {@code true} when a secret is configured
     */
    public boolean forwarding() {
        return secret != null;
    }

    /**
     * Names the state without the secret. The value is never printed, here or anywhere else in the server.
     *
     * @return a description that contains no part of the secret
     */
    @Override
    public String toString() {
        return forwarding()
                ? "ProxyForwardingSettings[forwarding, secret masked]"
                : "ProxyForwardingSettings[offline]";
    }

    private static ProxyForwardingSettings fromSource(String value, String name) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "%s must not be blank: set the secret, or unset the variable to run offline".formatted(name));
        }
        return new ProxyForwardingSettings(value);
    }
}
