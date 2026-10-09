package net.elytrarace.voyager.platform.catalog;

import java.util.List;

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
 */
public record CatalogReading(CatalogSnapshot snapshot, List<CatalogProblem> problems) {

    public CatalogReading {
        problems = List.copyOf(problems);
    }
}
