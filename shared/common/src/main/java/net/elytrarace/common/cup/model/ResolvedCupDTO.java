package net.elytrarace.common.cup.model;

import net.elytrarace.common.map.model.MapDTO;

import java.util.List;

public record ResolvedCupDTO(String name,
                             String displayName,
                             List<MapDTO> maps) implements CupDTO {
}
