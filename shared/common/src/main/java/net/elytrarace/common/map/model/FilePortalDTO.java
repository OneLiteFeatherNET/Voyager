package net.elytrarace.common.map.model;

import java.util.List;

public record FilePortalDTO(
        int index,
        List<LocationDTO> locations
) implements PortalDTO {

}
