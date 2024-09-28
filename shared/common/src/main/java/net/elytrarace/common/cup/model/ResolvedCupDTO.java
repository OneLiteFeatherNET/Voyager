package net.elytrarace.common.cup.model;

import net.elytrarace.common.map.model.MapDTO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;

public record ResolvedCupDTO(Key name,
                             Component displayName,
                             List<MapDTO> maps) implements CupDTO {
}
