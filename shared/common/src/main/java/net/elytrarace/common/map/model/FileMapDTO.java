package net.elytrarace.common.map.model;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.SortedSet;
import java.util.UUID;

public record FileMapDTO(
        UUID uuid,
        Key name,
        String world,
        Component displayName,
        Component author,
        SortedSet<PortalDTO> portals,
        @Nullable BoostConfigDTO boostConfig
) implements MapDTO {
}
