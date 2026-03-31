package net.elytrarace.server.cup;

import java.util.List;

/**
 * Defines a cup consisting of an ordered sequence of maps that players race through.
 *
 * @param name the display name of the cup
 * @param maps the ordered list of maps in this cup
 */
public record CupDefinition(String name, List<MapDefinition> maps) {
    public CupDefinition {
        maps = List.copyOf(maps);
    }
}
