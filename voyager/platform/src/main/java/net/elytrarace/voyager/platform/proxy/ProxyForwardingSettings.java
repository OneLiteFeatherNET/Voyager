package net.elytrarace.voyager.platform.proxy;

import net.elytrarace.voyager.platform.config.ConfigLoader;
import net.elytrarace.voyager.platform.proxy.exception.ProxySecretException;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Whether the server accepts player identity from a Velocity proxy, and the secret that signs it.
 *
 * <p>The secret is read from the first of four sources that is set, highest first: the file named by the environment variable
 * {@value #FILE_ENVIRONMENT_NAME}, the file named by the system property {@value #FILE_PROPERTY_NAME}, the environment variable
 * {@value #ENVIRONMENT_NAME}, and the system property {@value #PROPERTY_NAME}. A file's content is trimmed of surrounding
 * whitespace and read once, at boot. A set source that cannot be used (a blank value, a missing or unreadable file, an empty file)
 * is refused rather than treated as absent, so a mistyped secret can never quietly turn signature checking off. With no source set,
 * the server runs offline and the composition root logs a WARN.
 *
 * @param secret the shared secret, or {@code null} when the server runs offline
 * @param source the source the secret came from; {@link Source#NONE} exactly when {@code secret} is {@code null}
 */
public record ProxyForwardingSettings(@Nullable String secret, Source source) {

    /** The environment variable that names the secret. It wins over the property. */
    public static final String ENVIRONMENT_NAME = "VOYAGER_VELOCITY_SECRET";

    /** The system property that names the secret when no file and no environment variable is set. */
    public static final String PROPERTY_NAME = "voyager.velocity.secret";

    /** The environment variable that names a file holding the secret. It is the preferred source. */
    public static final String FILE_ENVIRONMENT_NAME = "VOYAGER_VELOCITY_SECRET_FILE";

    /** The system property that names a file holding the secret, when the environment variable names no file. */
    public static final String FILE_PROPERTY_NAME = "voyager.velocity.secret.file";

    /** The configuration keys these settings read, so the loader can hand over one view of them. */
    public static final List<String> KEYS = List.of(PROPERTY_NAME, FILE_PROPERTY_NAME);

    private static final String OFFLINE_WARNING = ("No Velocity secret is set (%s, %s or -D%s): this server runs in offline mode "
            + "and player UUIDs are not forwarded from a proxy. LuckPerms cannot find a proxied player's grants in that case.")
                    .formatted(FILE_ENVIRONMENT_NAME, ENVIRONMENT_NAME, PROPERTY_NAME);

    private static final String PROPERTY_WARNING = ("The Velocity secret is read from the system property %s, which every local "
            + "user can see in the process list; set %s to a file that holds the secret and is readable by the server user only, "
            + "and remove the property (docs/guides/how-to-run-behind-a-proxy.md)")
                    .formatted(PROPERTY_NAME, FILE_ENVIRONMENT_NAME);

    /** Where the secret came from, or that it came from nowhere. */
    public enum Source {
        /** No source is set: the server runs offline. */
        NONE,
        /** A file named by {@value #FILE_ENVIRONMENT_NAME} or {@value #FILE_PROPERTY_NAME}. */
        FILE,
        /** The environment variable {@value #ENVIRONMENT_NAME}. */
        ENVIRONMENT,
        /** The system property {@value #PROPERTY_NAME}. */
        PROPERTY
    }

    public ProxyForwardingSettings {
        Objects.requireNonNull(source, "source must not be null");
        if (secret != null && secret.isBlank()) {
            throw new IllegalArgumentException("the Velocity secret must not be blank");
        }
        if ((secret == null) != (source == Source.NONE)) {
            throw new IllegalArgumentException("the Velocity secret and its source must agree");
        }
    }

    /**
     * Reads the settings from the two maps. Each map holds the values of its source; the file keys are read first, then the
     * secret keys, and the first source that is set decides. A file is read from disk here.
     *
     * @param environment the process environment, by variable name
     * @param properties  the system properties, by name
     * @return the settings; offline with {@link Source#NONE} when no source is set
     * @throws ProxySecretException when the source that is set cannot be used
     */
    public static ProxyForwardingSettings from(Map<String, String> environment, Map<String, String> properties) {
        String fileFromEnvironment = environment.get(FILE_ENVIRONMENT_NAME);
        if (fileFromEnvironment != null) {
            return fromFile(fileFromEnvironment, FILE_ENVIRONMENT_NAME);
        }
        String fileFromProperty = properties.get(FILE_PROPERTY_NAME);
        if (fileFromProperty != null) {
            return fromFile(fileFromProperty, FILE_PROPERTY_NAME);
        }
        String fromEnvironment = environment.get(ENVIRONMENT_NAME);
        if (fromEnvironment != null) {
            return fromValue(fromEnvironment, ENVIRONMENT_NAME, Source.ENVIRONMENT);
        }
        String fromProperty = properties.get(PROPERTY_NAME);
        if (fromProperty != null) {
            return fromValue(fromProperty, PROPERTY_NAME, Source.PROPERTY);
        }
        return new ProxyForwardingSettings(null, Source.NONE);
    }

    /**
     * Reads the settings through the configuration loader, which resolves each key's sources in its own order. The environment is
     * handed over as the loader's environment, so a file or secret named by a variable still beats the property.
     *
     * @param loader the loader of the running server, or of a test
     * @return the settings; offline with {@link Source#NONE} when no source is set
     * @throws ProxySecretException when the source that is set cannot be used
     */
    public static ProxyForwardingSettings fromLoader(ConfigLoader loader) {
        return from(loader.environment(), loader.view(KEYS));
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
     * The WARN lines the composition roots log for these settings. None carries the secret's value: with no secret, the offline
     * line; with the secret from the system property, the line that recommends the file; otherwise none.
     *
     * @return the lines, in the order they are logged; empty when the settings need no warning
     */
    public List<String> warnings() {
        List<String> lines = new ArrayList<>();
        if (source == Source.NONE) {
            lines.add(OFFLINE_WARNING);
        }
        propertyWarning().ifPresent(lines::add);
        return List.copyOf(lines);
    }

    /**
     * The WARN for a secret taken from the system property, which names the file as the better source. Present only for
     * {@link Source#PROPERTY}.
     *
     * @return the line, without the value of the secret; empty for every other source
     */
    public Optional<String> propertyWarning() {
        return source == Source.PROPERTY ? Optional.of(PROPERTY_WARNING) : Optional.empty();
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

    private static ProxyForwardingSettings fromValue(String value, String name, Source source) {
        if (value.isBlank()) {
            throw new ProxySecretException(name,
                    "%s must not be blank: set the secret, or unset the variable to run offline".formatted(name));
        }
        return new ProxyForwardingSettings(value, source);
    }

    private static ProxyForwardingSettings fromFile(String path, String setting) {
        String named = path.strip();
        if (named.isEmpty()) {
            throw new ProxySecretException(setting,
                    "%s must not be blank: name the file that holds the secret, or unset it".formatted(setting));
        }
        String content;
        try {
            content = Files.readString(Path.of(named), StandardCharsets.UTF_8);
        } catch (IOException | InvalidPathException failure) {
            throw new ProxySecretException(setting, ("%s names '%s', which cannot be read (%s): the server refuses to start "
                    + "and does not run with another secret or offline").formatted(setting, named,
                            failure.getClass().getSimpleName()));
        }
        String secret = content.strip();
        if (secret.isEmpty()) {
            throw new ProxySecretException(setting, ("%s names '%s', which holds no secret: write the secret into the file, "
                    + "or unset %s").formatted(setting, named, setting));
        }
        return new ProxyForwardingSettings(secret, Source.FILE);
    }
}
