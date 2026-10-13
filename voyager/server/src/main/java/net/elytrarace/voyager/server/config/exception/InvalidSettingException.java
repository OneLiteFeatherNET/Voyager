package net.elytrarace.voyager.server.config.exception;

/**
 * A setting the server reads is present and cannot be used: a lobby that names a world without a spawn, a spawn that is
 * not three numbers, or a maximum racer count below the minimum.
 *
 * <p>The message names the setting by its environment name, and the text it read, so an operator sees which variable to
 * fix. {@link #setting()} is the same name, for the configuration check to key its problem under.
 */
public final class InvalidSettingException extends IllegalArgumentException {

    private final String setting;

    /**
     * @param setting the name of the setting at fault, as the operator sets it
     * @param message what is wrong with it, naming the setting
     */
    public InvalidSettingException(String setting, String message) {
        super(message);
        this.setting = setting;
    }

    /** The name of the setting at fault. */
    public String setting() {
        return setting;
    }
}
