package net.elytrarace.voyager.platform.catalog;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * What one read of a data directory found: the definitions that parsed, the cup files it saw, the file
 * each accepted definition came from, and every problem found.
 *
 * <p>Nothing here is thrown. {@link CatalogLoader#load} is the caller that refuses on the first
 * problem; boot reads {@link #cupFileProblems()} and {@link #catalogueProblems()} separately, because a
 * problem in one cup file is not a reason to refuse boot when that cup is not the one played.
 *
 * @param snapshot the definitions that parsed and are unique; a cup is kept whether or not the maps it
 *     names resolve
 * @param problems every problem found, in the order cups, maps; each directory's in sorted filename
 *     order
 * @param cupFiles every {@code .json} file in {@code cups/}, parsed or not, in sorted filename order
 * @param mapFilesByName the file each accepted map was read from, by the name it declares
 * @param cupFilesByName the file each accepted cup was read from, by the name it declares
 */
public record CatalogReading(CatalogSnapshot snapshot, List<CatalogProblem> problems, List<Path> cupFiles,
        Map<String, Path> mapFilesByName, Map<String, Path> cupFilesByName) {

    public CatalogReading {
        problems = List.copyOf(problems);
        cupFiles = List.copyOf(cupFiles);
        mapFilesByName = Map.copyOf(mapFilesByName);
        cupFilesByName = Map.copyOf(cupFilesByName);
    }

    /**
     * How many cup files the directory holds: the cups that parsed plus the files that did not. A file
     * whose name duplicates another's counts once for each file.
     */
    public int cupFileCount() {
        return cupFiles.size();
    }

    /** The problems that concern one cup file: unparseable files and duplicate names. */
    public List<CatalogProblem> cupFileProblems() {
        return problems.stream().filter(problem -> cupFiles.contains(problem.source())).toList();
    }

    /** Every other problem: a directory that is missing or empty, and every map file that is wrong. */
    public List<CatalogProblem> catalogueProblems() {
        return problems.stream().filter(problem -> !cupFiles.contains(problem.source())).toList();
    }
}
