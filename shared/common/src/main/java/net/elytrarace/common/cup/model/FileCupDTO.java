package net.elytrarace.common.cup.model;

import java.util.List;
import java.util.UUID;

public record FileCupDTO(
        String name,
        String displayName,
        List<UUID> maps
) implements CupDTO {
}
