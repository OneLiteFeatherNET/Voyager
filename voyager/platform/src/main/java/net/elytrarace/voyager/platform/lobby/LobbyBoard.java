package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * The text every lobby display is built from: the cup's display name, and its maps in play order, each by its display
 * name. The sidebar, the hologram and the boss bar read the same board, so they cannot disagree about which cup comes
 * next (ADR-0033).
 *
 * <p>A value with no Minestom type beyond {@link Component}. A map line is numbered by its place in the cup, and a cup with
 * more maps than the sidebar holds shows the first {@link #MAX_MAP_LINES} and one line that says how many follow.
 *
 * @param cupName  the display name of the cup
 * @param mapNames the display names of the maps of the cup, in the order they are played
 */
@NotNullByDefault
public record LobbyBoard(String cupName, List<String> mapNames) {

    /** The most map lines a board lists; a longer cup gets a "more" line after them (the sidebar's limit, design Q7). */
    public static final int MAX_MAP_LINES = 12;

    /**
     * @param cupName  the name of the cup
     * @param mapNames the maps of the cup, in play order
     */
    public LobbyBoard {
        mapNames = List.copyOf(mapNames);
    }

    /**
     * The board of a cup. Each map id of the cup is resolved to its map's display name in {@code snapshot}; a map the
     * snapshot does not hold keeps its id, so a board never fails for a name it cannot find.
     *
     * @param cup      the cup a round would play
     * @param snapshot the catalogue the cup's maps are read from
     * @return the board that names it
     */
    public static LobbyBoard of(CupDefinition cup, CatalogSnapshot snapshot) {
        List<String> mapNames = new ArrayList<>(cup.mapNames().size());
        for (String mapId : cup.mapNames()) {
            mapNames.add(snapshot.mapByName(mapId).map(MapDefinition::displayName).orElse(mapId));
        }
        return new LobbyBoard(cup.displayName(), mapNames);
    }

    /** The title the sidebar shows: the cup's name. */
    public Component title() {
        return Messages.lobbyBoardTitle(cupName);
    }

    /**
     * The map lines in play order, at most {@link #MAX_MAP_LINES} maps, then one "more" line when the cup has more.
     *
     * @return the lines, top to bottom
     */
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        int shown = Math.min(mapNames.size(), MAX_MAP_LINES);
        for (int index = 0; index < shown; index++) {
            lines.add(Messages.lobbyBoardMap(index + 1, mapNames.get(index)));
        }
        int more = mapNames.size() - shown;
        if (more > 0) {
            lines.add(Messages.lobbyBoardMore(more));
        }
        return List.copyOf(lines);
    }
}
