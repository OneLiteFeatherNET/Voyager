package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a data directory into a {@link LoadedCatalog} at boot, and into a {@link ReloadOutcome} on a reload.
 *
 * <p>Built on {@link CatalogLoader#read}, so the boot refusal and the reload report are the same reading, and
 * on {@link CupResolution}, so the cup is chosen the same way on both. Boot refuses by throwing, as it always
 * has. A reload never throws for a bad edit: it returns {@link ReloadOutcome.Rejected} with every problem the
 * reading and the checks found, so the operator gets one list.
 *
 * <p>The worlds of the chosen cup are checked and opened here, through {@link WorldOpener}, before a reload is
 * applied. A world that is already open is not reread; one whose region files changed is reported as needing a
 * restart. A world this attempt opened is discarded again if a later one fails.
 *
 * <p>Carries no DI annotation. The composition root constructs it with the clock and the world opener.
 */
public final class CatalogReloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogReloader.class);

    private final Clock clock;
    private final WorldOpener worlds;

    /**
     * @param clock  stamps {@link LoadedCatalog#loadedAt()}; a fixed clock in tests
     * @param worlds checks and opens the worlds the maps name
     */
    public CatalogReloader(Clock clock, WorldOpener worlds) {
        this.clock = clock;
        this.worlds = worlds;
    }

    /**
     * Boot's read: refuses with the first catalogue problem, as {@link CatalogLoader#load} does, and with the
     * played cup's own problems. A broken cup that is not played does not refuse; it is logged once.
     *
     * @param dataDirectory the directory holding {@code maps/} and {@code cups/}
     * @param chosen        the cup to play, or empty for the directory's only cup
     * @return the catalogue the server boots with
     * @throws RuntimeException the cause of the first catalogue problem, or the cup-selection refusal
     */
    public LoadedCatalog loadInitial(Path dataDirectory, Optional<String> chosen) {
        CatalogReading reading = CatalogLoader.read(dataDirectory);
        if (!reading.catalogueProblems().isEmpty()) {
            throw reading.catalogueProblems().getFirst().cause();
        }
        CupDefinition played = CupResolution.resolve(reading, chosen);
        Optional<UnresolvedCupMapException> unresolved = CatalogConsistency.unresolvedCupMaps(
                reading.snapshot().maps(), Map.of(played.name(), played));
        if (unresolved.isPresent()) {
            throw unresolved.get();
        }
        List<String> skipped = CupResolution.skippedCups(reading, played);
        if (!skipped.isEmpty()) {
            LOGGER.warn(CupResolution.skippedWarning(skipped));
        }
        return new LoadedCatalog(reading.snapshot(), played, clock.instant());
    }

    /**
     * A reload: reads the directory, checks the whole catalogue and the worlds of the chosen cup, and opens the
     * worlds that are not open yet. Never throws for a bad edit.
     *
     * <p>Every problem the reading finds is reported, not only those of the chosen cup, so a broken unplayed cup
     * file also rejects the reload until it is fixed. That is stricter than boot, on purpose; see the how-to.
     *
     * @param dataDirectory the directory holding {@code maps/} and {@code cups/}
     * @param chosen        the cup to play, or empty for the directory's only cup
     * @return {@link ReloadOutcome.Applied} with the new catalogue, or {@link ReloadOutcome.Rejected} with the problems
     */
    public ReloadOutcome reload(Path dataDirectory, Optional<String> chosen) {
        CatalogReading reading = CatalogLoader.read(dataDirectory);
        List<String> problems = new ArrayList<>();
        for (CatalogProblem problem : reading.problems()) {
            problems.add(CatalogValidation.problemOf(problem).format());
        }

        Optional<CupDefinition> played = resolveCup(reading, chosen, problems);
        // The cross-check is skipped while a file is broken, as CatalogLoader.load skips it: a map that does not
        // parse would otherwise be reported as a dangling entry of every cup that names it.
        if (reading.problems().isEmpty()) {
            CatalogConsistency.unresolvedCupMaps(reading.snapshot().maps(), reading.snapshot().cups())
                    .ifPresent(unresolved -> problems.add(problem(
                            dataDirectory.resolve("cups").toString(), "cup", unresolved.getMessage())));
        }
        if (played.isEmpty() || !problems.isEmpty()) {
            return new ReloadOutcome.Rejected(problems);
        }

        CupDefinition cup = played.get();
        Set<String> needed = worldsOf(cup, reading);
        for (String world : needed) {
            if (!worlds.holdsRegionData(world)) {
                problems.add(problem(world, "world", "holds no region data; the maps of cup '%s' need it"
                        .formatted(cup.name())));
            }
        }
        if (!problems.isEmpty()) {
            return new ReloadOutcome.Rejected(problems);
        }
        return openAndApply(reading, cup, needed);
    }

    private ReloadOutcome openAndApply(CatalogReading reading, CupDefinition cup, Set<String> needed) {
        List<String> warnings = new ArrayList<>();
        List<String> opened = new ArrayList<>();
        String current = null;
        try {
            for (String world : needed) {
                current = world;
                if (worlds.isOpen(world)) {
                    if (worlds.regionDataChanged(world)) {
                        warnings.add("restart needed for world '%s': its region files changed after it was opened"
                                .formatted(world));
                    }
                } else {
                    worlds.open(world);
                    opened.add(world);
                }
            }
        } catch (RuntimeException failure) {
            for (String world : opened) {
                discardQuietly(world);
            }
            return new ReloadOutcome.Rejected(List.of(problem(current, "world",
                    "could not be opened: %s".formatted(failure.getMessage()))));
        }
        return new ReloadOutcome.Applied(new LoadedCatalog(reading.snapshot(), cup, clock.instant()), warnings);
    }

    private void discardQuietly(String world) {
        try {
            worlds.discard(world);
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not discard world '{}' after a rejected reload", world, failure);
        }
    }

    /** The cup the reload would play, or empty with a problem added when none can be chosen. */
    private static Optional<CupDefinition> resolveCup(CatalogReading reading, Optional<String> chosen,
            List<String> problems) {
        try {
            return Optional.of(CupResolution.resolve(reading, chosen));
        } catch (RuntimeException refusal) {
            boolean alreadyListed = reading.problems().stream().anyMatch(problem -> problem.cause() == refusal);
            if (!alreadyListed) {
                problems.add(problem("cup selection", "cup", refusal.getMessage()));
            }
            return Optional.empty();
        }
    }

    /** The distinct worlds the cup's maps name, in rotation order. */
    private static Set<String> worldsOf(CupDefinition cup, CatalogReading reading) {
        Set<String> worlds = new LinkedHashSet<>();
        for (String name : cup.mapNames()) {
            reading.snapshot().mapByName(name).map(MapDefinition::world).ifPresent(worlds::add);
        }
        return worlds;
    }

    private static String problem(String source, String key, String message) {
        return new ConfigProblem(key, source, message, Severity.ERROR).format();
    }
}
