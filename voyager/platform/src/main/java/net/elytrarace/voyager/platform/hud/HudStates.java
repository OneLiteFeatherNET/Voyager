package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.race.cup.MapFigures;

/**
 * The one place a map's figures become the HUD state drawn from them. Every field is copied as it is: the race
 * decided the figures, and the platform only shows them.
 */
public final class HudStates {

    private HudStates() {
    }

    public static HudState of(MapFigures figures) {
        return new HudState(figures.gameTick(), figures.elapsed(), figures.ringsPassed(), figures.ringCount(),
                figures.lastRingTick(), figures.mapNumber(), figures.mapCount(), figures.mapName(),
                figures.outlook());
    }
}
