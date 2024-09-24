package net.elytrarace.common.utils;

import org.jetbrains.annotations.NotNull;

/**
 * The enum contains all formats which are supported by the {@link Strings#getTimeString(TimeFormat, int)} method.
 * Each enum entry contains a default format which contains the format for the timestamp zero.
 * @author theEvilReaper
 * @version 1.0.0
 * @since 1.2.0
 */
public enum TimeFormat {

    MM_SS("00:00"),
    HH_MM_SS("00:00:00");

    private final String defaultFormat;

    /**
     * Creates a new entry from the {@link TimeFormat} with the given format.
     * @param defaultFormat the format to set
     */
    TimeFormat(@NotNull String defaultFormat) {
        this.defaultFormat = defaultFormat;
    }

    /**
     * Returns the default format from the specific format.
     * @return the given format
     */
    public String getDefaultFormat() {
        return defaultFormat;
    }
}
