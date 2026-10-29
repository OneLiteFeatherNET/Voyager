package net.elytrarace.voyager.platform.hud;

import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.standing.MapStanding;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.scoreboard.Sidebar;

import org.jetbrains.annotations.NotNullByDefault;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The standings sidebar a racer reads while the next map is waited for: every racer who played the map, computer racers
 * included, finishers first by time with the gap to the leader, then the racers still racing, then those who did not
 * finish.
 *
 * <p>One sidebar per racer, shown from the moment the racer stands in the next map until that map's GO. It holds at most
 * {@link #MAX_LINES} lines, the client's limit, and when there are more racers than that its last line counts the racers
 * not shown. The sidebar is rebuilt when its lines change, and not otherwise.
 */
@NotNullByDefault
public final class BetweenMapsBoard {

    /** The most lines a sidebar holds: the client shows fifteen. */
    public static final int MAX_LINES = 15;

    /**
     * One line of the sidebar: its identifier, which is stable for the line's place, and its text.
     *
     * @param id the line's identifier
     * @param content the text the line shows
     */
    public record Line(String id, Component content) {
    }

    /** What a racer's sidebar shows, so that an unchanged standing is not sent again. */
    private record Shown(Sidebar sidebar, List<Line> lines) {
    }

    private final Map<UUID, Shown> sidebars = new HashMap<>();

    /**
     * The lines of the standings sidebar for a map's standing, top to bottom.
     *
     * @param standing the map's standing, with every racer who started it
     * @param names the name each racer is shown under, by id
     * @return at most {@link #MAX_LINES} lines
     */
    public static List<Line> lines(MapStanding standing, Function<UUID, Component> names) {
        List<Component> rows = new ArrayList<>();
        int place = 0;
        for (MapStanding.Entry entry : standing.entries()) {
            Component name = names.apply(entry.racer());
            switch (entry.outcome()) {
                case FINISHED -> {
                    place++;
                    Duration time = entry.finishedAt().elapsed();
                    Duration behind = standing.gapToLeader(entry.racer()).orElseThrow();
                    rows.add(Messages.waitStandingsFinisher(place, name, time, behind));
                }
                case RACING -> rows.add(Messages.waitStandingsRacing(name));
                case DID_NOT_FINISH -> rows.add(Messages.waitStandingsDnf(name));
            }
        }
        boolean overflow = rows.size() > MAX_LINES;
        int shown = overflow ? MAX_LINES - 1 : rows.size();
        List<Line> lines = new ArrayList<>(Math.min(rows.size(), MAX_LINES));
        for (int index = 0; index < shown; index++) {
            lines.add(new Line("racer-" + index, rows.get(index)));
        }
        if (overflow) {
            lines.add(new Line("more", Messages.waitStandingsMore(rows.size() - shown)));
        }
        return List.copyOf(lines);
    }

    /**
     * Shows the standings to {@code racer}, or rebuilds the sidebar when its lines have changed. The sidebar is kept
     * apart from the lobby's, which is shown only while the room waits.
     *
     * @param racer the racer the sidebar is for
     * @param lines the lines, from {@link #lines}
     * @return whether what the racer sees changed
     */
    public boolean update(Player racer, List<Line> lines) {
        UUID id = racer.getUuid();
        Shown current = sidebars.get(id);
        if (current != null && current.lines().equals(lines)) {
            return false;
        }
        if (current != null) {
            current.sidebar().removeViewer(racer);
        }
        Sidebar sidebar = new Sidebar(Messages.waitStandingsTitle());
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            // The first line has the highest score, so the lines read top to bottom; the blank format hides the number.
            sidebar.createLine(new Sidebar.ScoreboardLine(line.id(), line.content(), lines.size() - index,
                    Sidebar.NumberFormat.blank()));
        }
        sidebar.addViewer(racer);
        sidebars.put(id, new Shown(sidebar, lines));
        return true;
    }

    /**
     * Takes the standings down for {@code racer}, if one is shown.
     *
     * @return whether a sidebar was shown and is now hidden
     */
    public boolean hide(Player racer) {
        Shown removed = sidebars.remove(racer.getUuid());
        if (removed == null) {
            return false;
        }
        removed.sidebar().removeViewer(racer);
        return true;
    }

    /** Drops what is held for a racer who disconnected. */
    public void forget(UUID playerId) {
        sidebars.remove(playerId);
    }

    /**
     * The sidebar currently shown to {@code playerId}, if any.
     *
     * <p>Package-private, for {@code BetweenMapsBoardTest}.
     */
    Optional<Sidebar> sidebarOf(UUID playerId) {
        return Optional.ofNullable(sidebars.get(playerId)).map(Shown::sidebar);
    }
}
