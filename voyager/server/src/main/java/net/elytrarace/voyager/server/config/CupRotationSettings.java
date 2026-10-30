package net.elytrarace.voyager.server.config;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The cup rotation's two keys. {@code VOYAGER_CUP_ROTATION} names the cups a server plays, in play order, and
 * {@code VOYAGER_CUP_ROTATION_STATE} names the file that keeps the armed cup across a restart.
 *
 * <p>Both are read from the loader's view of the keys, as every setting is. A rotation and {@code VOYAGER_CUP} together
 * refuse boot: a rotation of one cup is what {@code VOYAGER_CUP} already says, so the two would disagree about the cup.
 *
 * @param cups      the rotation's entries as given, blanks dropped and surrounding spaces removed, in play order; empty when no
 *     rotation is configured
 * @param stateFile the state file, or empty when none is configured
 */
public record CupRotationSettings(List<String> cups, Optional<Path> stateFile) {

    /** The key that names the cups of the rotation, in play order. */
    public static final String ROTATION_PROPERTY = "VOYAGER_CUP_ROTATION";

    /** The key that names the state file that keeps the armed cup across a restart. */
    public static final String STATE_PROPERTY = "VOYAGER_CUP_ROTATION_STATE";

    public CupRotationSettings {
        cups = List.copyOf(cups);
    }

    /** Whether a rotation is configured at all: a rotation of one cup counts. */
    public boolean configured() {
        return !cups.isEmpty();
    }

    /**
     * The rotation settings the loader's values name. A key that is unset or blank is absent.
     *
     * @param properties the values by key, as {@code ConfigLoader.view} returns them
     * @return the settings; empty for both when neither key is set
     */
    public static CupRotationSettings from(Map<String, String> properties) {
        Optional<Path> state = Optional.ofNullable(blankToNull(properties.get(STATE_PROPERTY))).map(Path::of);
        return new CupRotationSettings(parse(properties.get(ROTATION_PROPERTY)), state);
    }

    /**
     * The problems the two keys have, each under its own key: both cup keys set, a rotation that names no cup, a state file
     * whose directory does not exist, and a state file with no rotation to keep (a warning).
     *
     * @param properties the values by key
     * @param sources    the label of the source of a key, as the configuration check prints it
     * @return every problem, in the order found
     */
    public static List<ConfigProblem> problems(Map<String, String> properties, Function<String, String> sources) {
        List<ConfigProblem> problems = new ArrayList<>();
        String rotation = blankToNull(properties.get(ROTATION_PROPERTY));
        String cup = blankToNull(properties.get(ServerSettings.CUP_PROPERTY));
        if (rotation != null && cup != null) {
            problems.add(new ConfigProblem(ROTATION_PROPERTY, sources.apply(ROTATION_PROPERTY),
                    "%s and %s are both set; remove one of them, because a rotation of one cup is %s alone"
                            .formatted(ServerSettings.CUP_PROPERTY, ROTATION_PROPERTY, ServerSettings.CUP_PROPERTY),
                    Severity.ERROR));
        }
        if (rotation != null && parse(rotation).isEmpty()) {
            problems.add(new ConfigProblem(ROTATION_PROPERTY, sources.apply(ROTATION_PROPERTY),
                    "%s names no cup; list the cups in play order, separated by commas".formatted(ROTATION_PROPERTY),
                    Severity.ERROR));
        }
        String state = blankToNull(properties.get(STATE_PROPERTY));
        if (state != null) {
            Path file = Path.of(state).toAbsolutePath();
            Path directory = file.getParent();
            if (directory == null || !Files.isDirectory(directory)) {
                problems.add(new ConfigProblem(STATE_PROPERTY, sources.apply(STATE_PROPERTY),
                        "the directory %s does not exist; create it, writable by the server, for %s"
                                .formatted(directory, STATE_PROPERTY), Severity.ERROR));
            }
            if (rotation == null) {
                problems.add(new ConfigProblem(STATE_PROPERTY, sources.apply(STATE_PROPERTY),
                        "%s is set without %s, so there is no rotation to keep and the file is not used"
                                .formatted(STATE_PROPERTY, ROTATION_PROPERTY), Severity.WARNING));
            }
        }
        return List.copyOf(problems);
    }

    /** The rotation's entries: split on commas, each trimmed, the blank ones dropped. */
    static List<String> parse(@Nullable String raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
