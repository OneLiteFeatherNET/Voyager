package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;

import java.util.ArrayList;
import java.util.List;

/**
 * The one check neither catalogue can make alone: that every map a cup plays actually exists.
 *
 * <p>Each catalogue validates its own files and is then done. A cup naming a map that no map file
 * provides is not malformed — it is a perfectly well-formed cup, in a directory of well-formed cups,
 * pointing at nothing. Left unchecked it is invisible until that map comes up mid-rotation, which is
 * precisely the defect the tree being replaced shipped, wearing a different costume: there, a map
 * whose world directory was missing was logged and skipped, and the cup quietly became one map
 * shorter.
 *
 * <p>A third type rather than a method on either catalogue, because the check needs both and neither
 * should learn about the other to get it. {@link JsonCupCatalog} is taken concretely — the check has
 * to walk every cup, and {@code CupCatalog} rightly offers no way to enumerate — while the maps
 * arrive as the {@link MapCatalog} port, since all this asks of them is whether a name resolves.
 * That asymmetry is the interface segregation rule read from the caller's side: take the narrowest
 * thing that answers the question.
 */
public final class CatalogConsistency {

    private CatalogConsistency() {
    }

    /**
     * Fails unless every map named by every cup resolves.
     *
     * @param cups the cups to check, all of them
     * @param maps the maps to resolve against
     * @throws UnresolvedCupMapException listing every cup entry that names a map the catalogue does
     *                                   not provide
     */
    public static void requireEveryCupMapResolves(JsonCupCatalog cups, MapCatalog maps) {
        List<String> problems = new ArrayList<>();
        for (String cupName : cups.cupNames()) {
            CupDefinition cup = cups.byName(cupName).orElseThrow(() -> new IllegalStateException(
                    "the cup catalogue listed '%s' and then did not provide it".formatted(cupName)));
            for (String mapName : cup.mapNames()) {
                if (maps.byName(mapName).isEmpty()) {
                    problems.add("cup '%s' plays '%s'".formatted(cupName, mapName));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new UnresolvedCupMapException(problems);
        }
    }
}
