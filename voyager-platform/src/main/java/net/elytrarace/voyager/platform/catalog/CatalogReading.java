package net.elytrarace.voyager.platform.catalog;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * What one read of a data directory found: the definitions that parsed, and every problem found.
 *
 * <p>Nothing here is thrown. {@link CatalogLoader#load} is the caller that refuses on the first
 * problem; a caller that wants all of them reads {@link #problems()}.
 *
 * @param snapshot the definitions that parsed and are unique; a cup is kept whether or not the maps it
 *     names resolve
 * @param problems every problem found, in the order cups, maps, cross-catalogue; each directory's in
 *     sorted filename order
 * @param mapFiles the file each accepted map was read from, by the name it declares
 * @param cupFiles the file each accepted cup was read from, by the name it declares
 */
public record CatalogReading(CatalogSnapshot snapshot, List<CatalogProblem> problems,
        Map<String, Path> mapFiles, Map<String, Path> cupFiles) {

    public CatalogReading {
        problems = List.copyOf(problems);
        mapFiles = Map.copyOf(mapFiles);
        cupFiles = Map.copyOf(cupFiles);
    }
}
