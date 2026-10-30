package net.elytrarace.voyager.platform.rotation;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.api.race.CatalogKeys;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogConsistency;
import net.elytrarace.voyager.platform.catalog.CatalogProblem;
import net.elytrarace.voyager.platform.catalog.CatalogReading;
import net.elytrarace.voyager.platform.catalog.CatalogValidation;
import net.elytrarace.voyager.platform.catalog.CupResolution;
import net.elytrarace.voyager.platform.world.WorldFolders;
import net.elytrarace.voyager.platform.world.WorldHealth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The boot check over every cup a rotation names, one cup at a time.
 *
 * <p>The rules, which the composition root turns into a refusal or a log line:
 * <ul>
 *   <li>A definition error in a rotation cup is an {@code ERROR}: a malformed file, a dangling map entry, a duplicate
 *       name, a name that resolves to no cup. A problem in a cup file the rotation does not name is a {@code WARNING}.</li>
 *   <li>A missing world (no folder, or no region data) skips its cup. It is one {@code WARNING} that names the cup and
 *       each missing world; the other cups stay playable.</li>
 *   <li>A world that is present but damaged is an {@code ERROR} that names the world.</li>
 *   <li>When no cup is playable, one {@code ERROR} lists each cup and its problem.</li>
 * </ul>
 *
 * <p>Each world is read through {@code health}, which in production closes the world again before it returns, so the
 * check holds one world at a time. The worlds of a cup are read in the cup's map order, and the cups in the rotation's
 * order. Each world is read once however many cups play it.
 *
 * <p>Every entry is resolved by the rule {@code VOYAGER_CUP} uses, so the flat id and the key form of a cup name give the
 * same cup, and a cup named twice in either spelling is one duplicate.
 */
public final class RotationValidation {

    private static final String CUP_KEY = "cup";
    private static final String WORLD_KEY = "world";
    private static final String JSON_SUFFIX = ".json";

    private RotationValidation() {
    }

    /** What the check found: every cup the rotation resolves, the ones that can be played and skipped, and every problem. */
    public record Result(List<String> cups, List<String> playable, List<String> skipped,
            List<ConfigProblem> problems) {

        public Result {
            cups = List.copyOf(cups);
            playable = List.copyOf(playable);
            skipped = List.copyOf(skipped);
            problems = List.copyOf(problems);
        }

        /** Whether boot is refused: an error was found, or no cup can be played. */
        public boolean refused() {
            return playable.isEmpty() || problems.stream().anyMatch(problem -> problem.severity() == Severity.ERROR);
        }
    }

    /**
     * Checks the cups a rotation names, and every catalogue problem.
     *
     * @param reading     the reading of the data directory
     * @param requested   the rotation's entries as the operator gave them, in order
     * @param worldsRoot  the directory the world directories sit in
     * @param cupsDirectory the cup directory, named in the problems that concern a cup
     * @param health      reads a world's health; called once per world that is present
     * @return the playable cups in the rotation's order, the skipped cups, and every problem, sorted
     */
    public static Result check(CatalogReading reading, List<String> requested, Path worldsRoot, Path cupsDirectory,
            CatalogValidation.HealthSource health) {
        Objects.requireNonNull(health, "health must not be null");
        List<ConfigProblem> problems = new ArrayList<>();
        String cupsSource = cupsDirectory.toString();

        // Every catalogue problem that is not a cup file: a directory that is missing, or a map file that is wrong.
        for (CatalogProblem problem : reading.catalogueProblems()) {
            problems.add(CatalogValidation.problemOf(problem));
        }

        Map<String, CupDefinition> resolved = resolveRotation(reading, requested, cupsSource, problems);
        Set<String> rotationStems = new LinkedHashSet<>();
        requested.forEach(entry -> rotationStems.add(CatalogKeys.normalise(entry)));
        // A cup file the rotation does not name is logged, never refused: the rotation does not play it.
        for (CatalogProblem problem : reading.cupFileProblems()) {
            if (!rotationStems.contains(CatalogKeys.normalise(stemOf(problem)))) {
                problems.add(asWarning(CatalogValidation.problemOf(problem)));
            }
        }

        List<String> playable = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        Map<String, Optional<String>> deepFailures = new HashMap<>();
        for (Map.Entry<String, CupDefinition> cup : resolved.entrySet()) {
            String name = cup.getKey();
            Optional<String> dangling = danglingEntry(reading, cup.getValue());
            if (dangling.isPresent()) {
                problems.add(error(CUP_KEY, cupsSource, dangling.get()));
                reasons.put(name, "names a map that no map file provides");
                continue;
            }
            List<String> missing = new ArrayList<>();
            boolean damaged = false;
            for (String mapName : cup.getValue().mapNames()) {
                MapDefinition map = reading.snapshot().mapByName(mapName).orElseThrow();
                switch (WorldFolders.check(worldsRoot, map.world())) {
                    case MISSING, NO_REGION_DATA -> {
                        if (!missing.contains(map.world())) {
                            missing.add(map.world());
                        }
                    }
                    case PRESENT -> {
                        Optional<String> failure = deepFailures.computeIfAbsent(map.world(),
                                world -> deepCheck(world, health));
                        if (failure.isPresent()) {
                            problems.add(error(WORLD_KEY, worldsRoot.toAbsolutePath().toString(), failure.get()));
                            damaged = true;
                        }
                    }
                }
            }
            if (damaged) {
                reasons.put(name, "has a damaged world, reported above");
            } else if (!missing.isEmpty()) {
                skipped.add(name);
                problems.add(warning(WORLD_KEY, worldsRoot.toAbsolutePath().toString(),
                        "cup '%s' is skipped for this boot: its world(s) %s are missing from %s"
                                .formatted(name, String.join(", ", missing), worldsRoot.toAbsolutePath())));
                reasons.put(name, "misses world(s) " + String.join(", ", missing));
            } else {
                playable.add(name);
            }
        }

        if (playable.isEmpty()) {
            List<String> each = new ArrayList<>();
            reasons.forEach((name, reason) -> each.add("'%s' %s".formatted(name, reason)));
            String summary = each.isEmpty() ? "the rotation names no cup that resolves" : String.join("; ", each);
            problems.add(error(CUP_KEY, cupsSource, "no cup of the rotation is playable: " + summary));
        }
        problems.sort(ConfigProblem.ORDER);
        return new Result(List.copyOf(resolved.keySet()), playable, skipped, problems);
    }

    /** The cups the rotation names, by canonical name, in order; an entry that does not resolve, or a second spelling, is a problem. */
    private static Map<String, CupDefinition> resolveRotation(CatalogReading reading, List<String> requested,
            String cupsSource, List<ConfigProblem> problems) {
        Map<String, CupDefinition> resolved = new LinkedHashMap<>();
        for (String entry : requested) {
            CupDefinition cup;
            try {
                cup = CupResolution.resolve(reading, Optional.of(entry));
            } catch (RuntimeException refusal) {
                problems.add(error(CUP_KEY, cupsSource, refusal.getMessage()));
                continue;
            }
            String canonical = cup.key().asString();
            if (resolved.containsKey(canonical)) {
                problems.add(error(CUP_KEY, cupsSource,
                        "cup '%s' is named twice in the rotation".formatted(canonical)));
                continue;
            }
            resolved.put(canonical, cup);
        }
        return resolved;
    }

    private static Optional<String> danglingEntry(CatalogReading reading, CupDefinition cup) {
        return CatalogConsistency.unresolvedCupMaps(reading.snapshot().maps(), Map.of(cup.key().asString(), cup))
                .map(exception -> exception.getMessage());
    }

    /** Empty when the world is sound; otherwise the reason it is not, naming the world. */
    private static Optional<String> deepCheck(String world, CatalogValidation.HealthSource health) {
        try {
            WorldHealth report = Objects.requireNonNull(health.healthOf(world), "the health source returned nothing");
            return report.isSound() ? Optional.empty()
                    : Optional.of("world '%s' is not sound: %s".formatted(world, report.describe()));
        } catch (RuntimeException failure) {
            String reason = Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getName());
            return Optional.of("world '%s' could not be read: %s".formatted(world, reason));
        }
    }

    private static String stemOf(CatalogProblem problem) {
        String name = problem.source().getFileName().toString();
        return name.endsWith(JSON_SUFFIX) ? name.substring(0, name.length() - JSON_SUFFIX.length()) : name;
    }

    private static ConfigProblem error(String key, String source, String message) {
        return new ConfigProblem(key, source, message, Severity.ERROR);
    }

    private static ConfigProblem warning(String key, String source, String message) {
        return new ConfigProblem(key, source, message, Severity.WARNING);
    }

    private static ConfigProblem asWarning(ConfigProblem problem) {
        return new ConfigProblem(problem.key(), problem.source(), problem.message(), Severity.WARNING);
    }
}
