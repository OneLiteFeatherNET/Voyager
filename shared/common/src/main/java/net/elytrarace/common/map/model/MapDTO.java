package net.elytrarace.common.map.model;

import java.util.SortedSet;
import java.util.UUID;

public record MapDTO(
        UUID uuid,
        String name,
        String world,
        String displayName,
        String author,
        SortedSet<PortalDTO> portals
) {
}
