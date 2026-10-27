package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.scoreboard.Sidebar;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The sidebar a waiting racer reads: the cup's name as its title, then the racers online out of the maximum, the maps in
 * play order, a status line (waiting, or the seconds to the start) and a footer. No line carries a score number.
 *
 * <p>One sidebar per racer, so its text is rendered in that racer's language. It is shown while the room waits in the place
 * racers wait (the lobby instance, or the first map when no lobby is configured). It is taken down when the racer leaves that
 * place, and while a cup races. The sidebar is rebuilt when the board changes, and only its racers and status lines are
 * updated when those change.
 */
@NotNullByDefault
public final class LobbySidebar {

    /**
     * Every line uses this format, so the score column is blank and no number shows (the owner's rule: no fake score numbers).
     * The line's score is only its place, which keeps the lines in order.
     */
    static final Sidebar.NumberFormat NUMBER_FORMAT = Sidebar.NumberFormat.blank();

    /**
     * One line of the sidebar: its id, which is stable for the line's place, and its text.
     *
     * @param id      the line's identifier
     * @param content the text the line shows
     */
    record Line(String id, Component content) {
    }

    /** What a racer's sidebar shows, so an unchanged board or status is not sent again. */
    private record Shown(Sidebar sidebar, LobbyBoard board, LobbyStatus status) {
    }

    private final Map<UUID, Shown> sidebars = new HashMap<>();

    /**
     * Shows the board to {@code racer} while the room waits in {@code waitingPlace}, and takes it down otherwise.
     *
     * @param racer        the racer the sidebar is for
     * @param waitingPlace the instance racers wait in
     * @param board        the cup's board
     * @param status       the racers online and the countdown, if one runs
     * @param waiting      whether the room is waiting, that is not racing
     * @return whether what the racer sees changed
     */
    public boolean update(Player racer, Instance waitingPlace, LobbyBoard board, LobbyStatus status, boolean waiting) {
        UUID id = racer.getUuid();
        boolean wanted = waiting && racer.getInstance() == waitingPlace;
        if (!wanted) {
            return hide(racer);
        }
        Shown current = sidebars.get(id);
        if (current != null && current.board().equals(board) && current.status().equals(status)) {
            return false;
        }
        if (current != null && current.board().equals(board)) {
            // Only the racers line and the status line change, so the sidebar is updated in place.
            current.sidebar().updateLineContent("racers", Messages.lobbySidebarRacers(status.online(), status.maximum()));
            current.sidebar().updateLineContent("status", statusLine(status));
            sidebars.put(id, new Shown(current.sidebar(), board, status));
            return true;
        }
        if (current != null) {
            current.sidebar().removeViewer(racer);
        }
        Sidebar sidebar = build(board, status);
        sidebar.addViewer(racer);
        sidebars.put(id, new Shown(sidebar, board, status));
        return true;
    }

    /**
     * Takes the sidebar down for {@code racer}, if one is shown.
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
     * <p>Package-private, for {@code LobbySidebarTest}.
     */
    Optional<Sidebar> sidebarOf(UUID playerId) {
        return Optional.ofNullable(sidebars.get(playerId)).map(Shown::sidebar);
    }

    /**
     * The lines of a sidebar for a board and a status, top to bottom: the racers, a blank, the map lines, a blank, the status
     * and the footer. At most fifteen, which is the client's limit.
     *
     * @param board  the cup's board
     * @param status the racers online and the countdown, if one runs
     * @return the lines in order
     */
    static List<Line> lines(LobbyBoard board, LobbyStatus status) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("racers", Messages.lobbySidebarRacers(status.online(), status.maximum())));
        lines.add(new Line("blank-top", Component.empty()));
        List<Component> maps = board.lines();
        for (int index = 0; index < maps.size(); index++) {
            lines.add(new Line("map-" + index, maps.get(index)));
        }
        lines.add(new Line("blank-bottom", Component.empty()));
        lines.add(new Line("status", statusLine(status)));
        lines.add(new Line("footer", Messages.lobbySidebarFooter()));
        return List.copyOf(lines);
    }

    private static Component statusLine(LobbyStatus status) {
        return status.countdown() == null
                ? Messages.lobbySidebarStatusWaiting()
                : Messages.lobbySidebarStatusCountdown(status.countdown());
    }

    private static Sidebar build(LobbyBoard board, LobbyStatus status) {
        Sidebar sidebar = new Sidebar(board.title());
        List<Line> lines = lines(board, status);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            // The first line is the highest score, so the lines read top to bottom; the blank format hides the number.
            sidebar.createLine(new Sidebar.ScoreboardLine(line.id(), line.content(), lines.size() - index, NUMBER_FORMAT));
        }
        return sidebar;
    }
}
