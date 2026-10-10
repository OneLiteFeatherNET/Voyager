package net.elytrarace.server.cup;

import net.elytrarace.common.game.mode.GameMode;

import java.util.List;
import java.util.Objects;

/**
 * Defines a cup consisting of an ordered sequence of maps that players race through.
 *
 * @param name the display name of the cup
 * @param mode the gameplay mode this cup runs under (RACE, PRACTICE, ...)
 * @param maps the ordered list of maps in this cup
 */
public record CupDefinition(String name, GameMode mode, List<MapDefinition> maps) {
    public CupDefinition {
        Objects.requireNonNull(mode, "mode must not be null");
        maps = List.copyOf(maps);
    }
}
