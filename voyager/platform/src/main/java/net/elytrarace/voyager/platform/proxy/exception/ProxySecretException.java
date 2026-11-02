package net.elytrarace.voyager.platform.proxy.exception;

/**
 * A secret source is set and cannot be used: a file that is missing, unreadable, not UTF-8 or empty, or a blank value. The
 * server refuses to start rather than run with another source or offline.
 *
 * <p>The message names the setting and, for a file, its path. It never names the content of the file or the value of the
 * secret. {@link #setting()} is the setting's name, for the configuration check to key its problem under.
 */
public final class ProxySecretException extends IllegalArgumentException {

    private final String setting;

    /**
     * @param setting the name of the setting at fault, as the operator sets it
     * @param message what is wrong with it, naming the setting and never the secret
     */
    public ProxySecretException(String setting, String message) {
        super(message);
        this.setting = setting;
    }

    /** The name of the setting at fault. */
    public String setting() {
        return setting;
    }
}
