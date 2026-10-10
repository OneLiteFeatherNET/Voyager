package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.race.cup.exception.UnresolvedCupException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which cup this server plays, and what happens to the cups it does not.
 *
 * <p>Takes the {@link CatalogReading} rather than the {@code CupCatalog} port: resolution needs the
 * cup files themselves, parsed or not, to count them and to name the one that will not parse. The port
 * is the right shape for everything that plays a cup; this is the one caller that has to choose one.
 *
 * <p><strong>Rules, in order.</strong> A named selection resolves to the cup of that name; a file that
 * does not parse and is named after it is refused by name, and so is a second file declaring the same
 * name. An unnamed selection resolves only when the directory holds exactly one cup file, counting
 * the ones that do not parse. Nothing else is played.
 *
 * <p><strong>A cup of any length.</strong> Nothing here — and nothing downstream of it — looks at
 * how many maps the resolved cup has. The repository currently ships one cup listing one map, which
 * means the map-to-map advance this rebuild exists to fix cannot be demonstrated from the committed
 * data; it does not mean one map is a special case in the code.
 */
@ApiStatus.Internal
public abstract class CupResolution {

    private static final String JSON_SUFFIX = ".json";

    private CupResolution() {
    }

    /**
     * Resolves the cup named by {@code chosen}, or the directory's only cup file when nothing named one.
     *
     * @throws UnresolvedCupException if the name resolves to nothing, or if nothing named a cup and
     *     the cup directory does not hold exactly one cup file
     * @throws RuntimeException the problem of a cup file this selection names: a malformed file, or a
     *     duplicate name
     */
    /**
     * The refusal of a cup selection, as the server reports it: the message of the {@link UnresolvedCupException} that
     * {@link #resolve} throws, or empty when the selection resolves. Any other failure is rethrown unchanged.
     *
     * <p>This is what lets the composition root report a refused selection without naming the exception type, which
     * the architecture keeps out of the server module.
     */
    public static Optional<String> refusalOf(CatalogReading reading, Optional<String> chosen) {
        try {
            resolve(reading, chosen);
            return Optional.empty();
        } catch (UnresolvedCupException exception) {
            return Optional.of(exception.getMessage());
        }
    }

    @Contract(pure = true)
    public static CupDefinition resolve(CatalogReading reading, Optional<String> chosen) {
        CatalogSnapshot catalog = reading.snapshot();
        if (chosen.isPresent()) {
            String name = chosen.get();
            Optional<CupDefinition> parsed = catalog.cupByName(name);
            if (parsed.isPresent()) {
                for (CatalogProblem problem : reading.cupFileProblems()) {
                    if (problem.cause() instanceof DuplicateCatalogEntryException duplicate
                            && duplicate.name().equals(name)) {
                        throw duplicate;
                    }
                }
                return parsed.get();
            }
            for (CatalogProblem problem : reading.cupFileProblems()) {
                if (stemOf(problem.source()).equals(name)) {
                    throw problem.cause();
                }
            }
            throw UnresolvedCupException.noSuchCup(name, catalog.cupNames());
        }
        if (reading.cupFileCount() != 1) {
            throw UnresolvedCupException.ambiguous(catalog.cupNames(), fileNames(reading));
        }
        if (catalog.cupNames().isEmpty()) {
            // The only file is the one that does not parse, so it is the cup that would have been played.
            throw reading.cupFileProblems().getFirst().cause();
        }
        return catalog.cupByName(catalog.cupNames().iterator().next())
                .orElseThrow(() -> new IllegalStateException("a cup name the catalogue just listed resolved to nothing"));
    }

    /**
     * What the boot warning lists for the cups that are not played: every cup file that does not parse,
     * and every dangling map entry of every other cup. Resolution has already refused any problem that
     * concerns the played cup, so nothing here does.
     *
     * @param reading the reading the played cup was resolved from
     * @param played  the cup the server plays
     * @return one line per problem, in cup-file order, then the dangling entries of the other cups
     */
    public static List<String> skippedCups(CatalogReading reading, CupDefinition played) {
        List<String> skipped = new ArrayList<>();
        for (CatalogProblem problem : reading.cupFileProblems()) {
            skipped.add(problem.message());
        }
        skipped.addAll(CatalogConsistency.unresolvedInOtherCups(
                played, reading.snapshot().maps(), reading.snapshot().cups()));
        return skipped;
    }

    /** The one line the composition root logs for {@link #skippedCups}. */
    public static String skippedWarning(List<String> skipped) {
        return "%s cup(s) are not playable and were skipped: %s".formatted(skipped.size(), String.join("; ", skipped));
    }

    private static List<String> fileNames(CatalogReading reading) {
        return reading.cupFiles().stream().map(Path::getFileName).map(Path::toString).toList();
    }

    private static String stemOf(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(JSON_SUFFIX) ? name.substring(0, name.length() - JSON_SUFFIX.length()) : name;
    }
}
