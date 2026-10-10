package net.elytrarace.common.utils;

import java.util.Arrays;
import java.util.function.Predicate;

public enum RunMode {
    GAME,
    SETUP,
    UNKNOWN;

    private static RunMode[] VALUES = values();


    public static RunMode getRunModeFromProperty() {
        String runModeProperty = System.getProperty("VOYAGER.RUN_MODE");
        Predicate<RunMode> matchProperty = (value) -> value.name().equalsIgnoreCase(runModeProperty);
        return Arrays.stream(VALUES).filter(matchProperty).findFirst().orElse(UNKNOWN);
    }
}
