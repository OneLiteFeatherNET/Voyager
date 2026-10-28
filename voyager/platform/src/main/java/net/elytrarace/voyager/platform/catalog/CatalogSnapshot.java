package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CatalogKeys;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The maps and cups one data directory provided, as an immutable value.
 *
 * <p>Built once by {@link CatalogLoader} and then only read. Both collections are copied on
 * construction, so the snapshot does not change when the map it was built from does, and both are
 * unmodifiable, so no caller can add, replace or remove a definition after the load.
 *
 * <p>Insertion order is kept: a snapshot lists its maps and cups in the order they were given, which
 * is the sorted filename order the loader reads them in.
 *
 * @param maps the map definitions, keyed by the key each one declares, in its full form
 * @param cups the cup definitions, keyed by the key each one declares, in its full form
 */
public record CatalogSnapshot(Map<String, MapDefinition> maps, Map<String, CupDefinition> cups) {

    public CatalogSnapshot {
        maps = Collections.unmodifiableMap(byKeyString(maps));
        cups = Collections.unmodifiableMap(byKeyString(cups));
    }

    /** A copy keyed by the full key string, so a flat id given as a key resolves like the key it stands for. */
    private static <T> Map<String, T> byKeyString(Map<String, T> definitions) {
        Map<String, T> keyed = new LinkedHashMap<>();
        definitions.forEach((name, definition) -> keyed.put(CatalogKeys.normalise(name), definition));
        return keyed;
    }

    /** Returns the map named {@code name}, a flat id or a key, or empty if no map file declares it. */
    public Optional<MapDefinition> mapByName(String name) {
        return Optional.ofNullable(maps.get(CatalogKeys.normalise(name)));
    }

    /** Returns the cup named {@code name}, a flat id or a key, or empty if no cup file declares it. */
    public Optional<CupDefinition> cupByName(String name) {
        return Optional.ofNullable(cups.get(CatalogKeys.normalise(name)));
    }

    /** Every map name in this snapshot, in the order they were given. */
    public @Unmodifiable Set<String> mapNames() {
        return maps.keySet();
    }

    /** Every cup name in this snapshot, in the order they were given. */
    public @Unmodifiable Set<String> cupNames() {
        return cups.keySet();
    }
}
