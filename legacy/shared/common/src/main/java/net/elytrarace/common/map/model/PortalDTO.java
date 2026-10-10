package net.elytrarace.common.map.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PortalDTO extends Comparable<PortalDTO>{

    int index();

    List<LocationDTO> locations();

    /**
     * Portal type as a raw string (e.g. "STANDARD", "BOOST", "CHECKPOINT").
     * Returns {@code null} for legacy portals that were persisted before portal
     * types existed; callers must handle the null case and fall back to a default.
     */
    default String type() {
        return null;
    }

    @Override
    default int compareTo(@NotNull PortalDTO o) {
        return Integer.compare(index(), o.index());
    }
}
