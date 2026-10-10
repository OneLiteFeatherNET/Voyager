package net.elytrarace.common.map.model;

import java.util.List;

public record FilePortalDTO(
        int index,
        List<LocationDTO> locations,
        String type
) implements PortalDTO {

    public FilePortalDTO(int index, List<LocationDTO> locations) {
        this(index, locations, null);
    }
}
