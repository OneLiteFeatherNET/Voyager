package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The restart after each finished cup, from {@code VOYAGER_RESTART_AFTER_CUP} (ADR-0047). Off unless the key is {@code true}.
 *
 * <p>Any other value is an error rather than off: {@code Boolean.parseBoolean} would read a typo such as {@code ture} as off,
 * and a restart that silently does not happen is the failure this key must not have. With more than one cup in
 * {@code VOYAGER_CUP_ROTATION}, the restart also needs {@code VOYAGER_CUP_ROTATION_STATE}: without the state every restart
 * would begin again at the first cup, and the rotation would never reach its second.
 *
 * @param afterCup whether the service ends its process after each finished cup
 */
public record RestartSettings(boolean afterCup) {

    /** The key that turns the restart after each finished cup on. */
    public static final String PROPERTY = "VOYAGER_RESTART_AFTER_CUP";

    /**
     * The restart setting the loader's values name. A key that is unset or blank is off.
     *
     * @param properties the values by key, as {@code ConfigLoader.view} returns them
     * @return the setting; off unless the key is {@code true}, in any case
     */
    public static RestartSettings from(Map<String, String> properties) {
        String raw = blankToNull(properties.get(PROPERTY));
        return new RestartSettings(raw != null && "true".equalsIgnoreCase(raw.strip()));
    }

    /**
     * The problems the key has: a value other than {@code true} or {@code false} (an error under the key), and a restart with a
     * rotation of more than one cup and no state file (an error under the key that names both keys).
     *
     * @param properties the values by key
     * @param sources    the label of the source of a key, as the configuration check prints it
     * @return every problem, in the order found
     */
    public static List<ConfigProblem> problems(Map<String, String> properties, Function<String, String> sources) {
        List<ConfigProblem> problems = new ArrayList<>();
        String raw = blankToNull(properties.get(PROPERTY));
        if (raw != null && !"true".equalsIgnoreCase(raw.strip()) && !"false".equalsIgnoreCase(raw.strip())) {
            problems.add(new ConfigProblem(PROPERTY, sources.apply(PROPERTY),
                    "%s must be true or false, was '%s'".formatted(PROPERTY, raw), Severity.ERROR));
        }
        if (from(properties).afterCup()) {
            CupRotationSettings rotation = CupRotationSettings.from(properties);
            if (rotation.cups().size() > 1 && rotation.stateFile().isEmpty()) {
                problems.add(new ConfigProblem(PROPERTY, sources.apply(PROPERTY),
                        ("%s is true and %s lists more than one cup, so %s must be set: without it every restart begins "
                                + "again at the first cup").formatted(PROPERTY, CupRotationSettings.ROTATION_PROPERTY,
                                CupRotationSettings.STATE_PROPERTY),
                        Severity.ERROR));
            }
        }
        return List.copyOf(problems);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
