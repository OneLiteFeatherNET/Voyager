package net.elytrarace.voyager.platform.catalog;

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
 * @param maps the map definitions, keyed by the name each one declares
 * @param cups the cup definitions, keyed by the name each one declares
 */
public record CatalogSnapshot(Map<String, MapDefinition> maps, Map<String, CupDefinition> cups) {

    public CatalogSnapshot {
        maps = Collections.unmodifiableMap(new LinkedHashMap<>(maps));
        cups = Collections.unmodifiableMap(new LinkedHashMap<>(cups));
    }

    /** Returns the map named {@code name}, or empty if no map file declares it. */
    public Optional<MapDefinition> mapByName(String name) {
        return Optional.ofNullable(maps.get(name));
    }

    /** Returns the cup named {@code name}, or empty if no cup file declares it. */
    public Optional<CupDefinition> cupByName(String name) {
        return Optional.ofNullable(cups.get(name));
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
