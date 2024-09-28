package net.elytrarace.common.cup.model;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;

public record FileCupDTO(
        Key name,
        Component displayName,
        List<UUID> maps
) implements CupDTO {
}
