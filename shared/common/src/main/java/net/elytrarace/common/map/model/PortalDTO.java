package net.elytrarace.common.map.model;

import java.util.List;

public record PortalDTO(
        int index,
        List<LocationDTO> locations
) implements Comparable<PortalDTO> {

    @Override
    public int compareTo(PortalDTO o) {
        return Integer.compare(index, o.index);
    }
}
