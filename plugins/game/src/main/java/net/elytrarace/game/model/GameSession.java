package net.elytrarace.game.model;

import net.elytrarace.common.cup.model.ResolvedCupDTO;

import java.util.UUID;

public record GameSession(
        UUID uuid,
        ResolvedCupDTO currentCup,
        GameMapDTO currentMap
) {

    public static GameSession fromWithCurrentCup(GameSession gameSession, ResolvedCupDTO currentCup) {
        return new GameSession(gameSession.uuid, currentCup, gameSession.currentMap);
    }

    public static GameSession fromWithCurrentMap(GameSession gameSession, GameMapDTO currentMap) {
        return new GameSession(gameSession.uuid, gameSession.currentCup, currentMap);
    }
}
