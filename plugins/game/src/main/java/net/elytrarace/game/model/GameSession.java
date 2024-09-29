package net.elytrarace.game.model;

import net.elytrarace.common.cup.model.CupDTO;

import java.util.UUID;

public record GameSession(
        UUID uuid,
        CupDTO currentCup,
        GameMapDTO currentMap
) {
}
