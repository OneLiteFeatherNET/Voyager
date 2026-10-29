package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.mapsetup.MapId;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ids whose import is running. A claim is taken on the main thread and released on the import's thread, so the set
 * is concurrent. The guard is in memory and per instance: nothing is shared between two guards, and a restart frees every
 * id. The claim registry of {@code single-builder-map-claims} replaces it.
 */
public final class ImportGuard {

    private final Set<MapId> claimed = ConcurrentHashMap.newKeySet();

    /**
     * Claims an id for an import.
     *
     * @param id the map id
     * @return true when the id was free and is now claimed; false when an import already holds it
     */
    public boolean claim(MapId id) {
        return claimed.add(id);
    }

    /**
     * Frees an id, whether its import succeeded or failed.
     *
     * @param id the map id
     */
    public void release(MapId id) {
        claimed.remove(id);
    }

    /**
     * @param id the map id
     * @return true while an import of the id holds it
     */
    public boolean isClaimed(MapId id) {
        return claimed.contains(id);
    }
}
