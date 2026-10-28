package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.text.DisplayName;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The text every lobby display is built from: the cup's coloured display name, and its maps in play order, each with its
 * coloured display name and its authors. The sidebar and the hologram read the same board, so they cannot disagree about
 * which cup comes next (ADR-0033).
 *
 * <p>The sidebar lists at most {@link #MAX_MAP_LINES} map lines: a cup of more maps shows the first nine and one line that
 * says how many follow. A map line drops its authors when the line would be longer than the sidebar's forty visible
 * characters, so the name is never cut. The hologram shows the first {@link #MAX_MAP_LINES} names, with no authors and no
 * "more" line, because it is one shared entity.
 *
 * @param cupName the coloured display name of the cup
 * @param maps    the maps of the cup, in the order they are played
 */
@NotNullByDefault
public record LobbyBoard(Component cupName, List<BoardMap> maps) {

    /** The most map lines the sidebar lists, including the "more" line when the cup has more maps than that. */
    public static final int MAX_MAP_LINES = 10;

    /** The visible width a sidebar line may take, the same forty characters a display name may show. */
    static final int LINE_WIDTH = 40;

    /** The widest "by"/"von" join the line may carry; the German word is the longer, so the English line fits too. */
    private static final String AUTHOR_JOIN = " · von ";

    /**
     * One map of the cup on the board.
     *
     * @param name    the map's coloured display name
     * @param authors the authors as one line of text, such as "TheMeinerLP +1", or empty when the map has none
     */
    public record BoardMap(Component name, String authors) {
    }

    /**
     * @param cupName the coloured display name of the cup
     * @param maps    the maps of the cup, in play order
     */
    public LobbyBoard {
        maps = List.copyOf(maps);
    }

    /**
     * The board of a cup. Each map id of the cup is resolved to its map in {@code snapshot}; a map the snapshot does not hold
     * shows its id, so a board never fails for a name it cannot find.
     *
     * @param cup      the cup a round would play
     * @param snapshot the catalogue the cup's maps are read from
     * @return the board that names it
     */
    public static LobbyBoard of(CupDefinition cup, CatalogSnapshot snapshot) {
        List<BoardMap> maps = new ArrayList<>(cup.mapNames().size());
        for (String mapId : cup.mapNames()) {
            maps.add(snapshot.mapByName(mapId)
                    .map(map -> new BoardMap(DisplayName.component(map.key(), map.displayName()), authorsOf(map)))
                    .orElseGet(() -> new BoardMap(DisplayName.component(Key.key(mapId), mapId), "")));
        }
        return new LobbyBoard(DisplayName.component(cup.key(), cup.displayName()), maps);
    }

    /** The title the sidebar shows: the cup's coloured name. */
    public Component title() {
        return Messages.lobbyBoardTitle(cupName);
    }

    /**
     * The map lines of the sidebar in play order: every map when there are at most {@link #MAX_MAP_LINES}, otherwise the
     * first nine and one "more" line that counts the rest.
     *
     * @return the lines, top to bottom
     */
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        int shown = maps.size() > MAX_MAP_LINES ? MAX_MAP_LINES - 1 : maps.size();
        for (int index = 0; index < shown; index++) {
            BoardMap map = maps.get(index);
            lines.add(Messages.lobbyBoardMap(index + 1, map.name(), authorsThatFit(index + 1, map)));
        }
        int more = maps.size() - shown;
        if (more > 0) {
            lines.add(Messages.lobbyBoardMore(more));
        }
        return List.copyOf(lines);
    }

    /**
     * The hologram's lines below its title: the coloured names of the first {@link #MAX_MAP_LINES} maps, with no number,
     * no authors and no "more" line.
     *
     * @return the names, top to bottom
     */
    public List<Component> hologramLines() {
        return maps.stream().limit(MAX_MAP_LINES).map(map -> Messages.lobbyHologramName(map.name())).toList();
    }

    /** The authors of a map as one line: the first name, and " +n" for the rest. Empty when the map has no authors. */
    static String authorsOf(MapDefinition map) {
        List<Author> authors = map.authors();
        if (authors.isEmpty()) {
            return "";
        }
        String first = authors.getFirst().name();
        return authors.size() == 1 ? first : "%s +%d".formatted(first, authors.size() - 1);
    }

    /** The authors of {@code map} when its whole line fits in {@link #LINE_WIDTH} visible characters, otherwise {@code null}. */
    private static @Nullable String authorsThatFit(int position, BoardMap map) {
        if (map.authors().isEmpty()) {
            return null;
        }
        String plainName = PlainTextComponentSerializer.plainText().serialize(map.name());
        int width = "%d. ".formatted(position).length() + plainName.codePointCount(0, plainName.length())
                + AUTHOR_JOIN.length() + map.authors().codePointCount(0, map.authors().length());
        return width <= LINE_WIDTH ? map.authors() : null;
    }
}
