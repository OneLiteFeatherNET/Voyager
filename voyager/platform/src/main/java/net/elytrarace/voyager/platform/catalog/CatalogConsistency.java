package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The one check neither directory can make alone: that every map a cup plays actually exists.
 *
 * <p>Each directory validates its own files and is then done. A cup naming a map that no map file
 * provides is not malformed — it is a well-formed cup pointing at nothing. Left unchecked it is
 * invisible until that map comes up mid-rotation.
 *
 * <p>Called by {@link CatalogLoader#load} once both directories have been read, and by the server's boot
 * for the one cup it plays and for the cups it skips, so neither directory has to learn about the other
 * to get the check.
 */
public final class CatalogConsistency {

    private CatalogConsistency() {
    }

    /**
     * Finds every cup entry that names a map the catalogue does not provide.
     *
     * @param maps the map definitions by name
     * @param cups the cup definitions by name
     * @return the exception listing every dangling entry, or empty when every entry resolves
     */
    public static Optional<UnresolvedCupMapException> unresolvedCupMaps(
            Map<String, MapDefinition> maps, Map<String, CupDefinition> cups) {
        List<String> problems = danglingEntries(maps, cups);
        return problems.isEmpty() ? Optional.empty() : Optional.of(new UnresolvedCupMapException(problems));
    }

    /**
     * Describes every dangling entry of every cup except {@code selected}, as values.
     *
     * <p>Used at boot for the cups that are not played: their dangling entries become one warning, not a
     * refusal. Nothing is thrown and nothing is logged here.
     *
     * @param selected the cup the server plays; its own entries are not in the result
     * @param maps     the map definitions by name
     * @param cups     every cup definition by name, including {@code selected}
     * @return one {@code "cup 'x' plays 'y'"} line per dangling entry of the other cups, in cup order
     */
    public static List<String> unresolvedInOtherCups(
            CupDefinition selected, Map<String, MapDefinition> maps, Map<String, CupDefinition> cups) {
        Map<String, CupDefinition> others = new LinkedHashMap<>(cups);
        others.remove(selected.name());
        return danglingEntries(maps, others);
    }

    private static List<String> danglingEntries(Map<String, MapDefinition> maps, Map<String, CupDefinition> cups) {
        List<String> problems = new ArrayList<>();
        for (CupDefinition cup : cups.values()) {
            for (String mapName : cup.mapNames()) {
                if (!maps.containsKey(mapName)) {
                    problems.add("cup '%s' plays '%s'".formatted(cup.name(), mapName));
                }
            }
        }
        return problems;
    }
}
