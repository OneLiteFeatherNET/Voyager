package net.elytrarace.common.map.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PortalDTO extends Comparable<PortalDTO>{

    int index();

    List<LocationDTO> locations();

    @Override
    default int compareTo(@NotNull PortalDTO o) {
        return Integer.compare(index(), o.index());
    }
}
