package net.elytrarace.common.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;

public final class Strings {

    private static final int TIME_DIVIDER = 60;

    private static final char SPACE = ' ';
    public static final String UTF_8_HEART = "\u2665";

    private Strings() {}


    /**
     * Convert a time value into the given format from the {@link TimeFormat} entry.
     * @param time The time who should be converted
     * @return The converted time
     */
    @Contract(pure = true)
    public static @NotNull String getTimeString(@NotNull TimeFormat timeFormat, int time) {
        if (time <= 0) {
            return timeFormat.getDefaultFormat();
        }

        int minutes = time / TIME_DIVIDER;
        int seconds = time % TIME_DIVIDER;

        StringJoiner stringJoiner = new StringJoiner(":");

        if (timeFormat == TimeFormat.HH_MM_SS) {
            int hours = minutes / TIME_DIVIDER;
            minutes = minutes % TIME_DIVIDER;
            stringJoiner.add(String.format("%02d", hours));
        }
        stringJoiner.add(String.format("%02d", minutes));
        stringJoiner.add(String.format("%02d", seconds));

        return stringJoiner.toString();
    }

}
