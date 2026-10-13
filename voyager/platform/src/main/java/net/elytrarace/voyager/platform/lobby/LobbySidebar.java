package net.elytrarace.voyager.platform.lobby;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.scoreboard.Sidebar;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The sidebar a waiting racer reads: the cup's name as its title, and its maps in play order as its lines.
 *
 * <p>One sidebar per racer. It is shown while the room waits and the racer stands in the place racers wait (the lobby
 * instance, or the first map when no lobby is configured). It is taken down when the racer leaves that place, and while a
 * cup races, so a race shows no sidebar of its own, as {@code RaceHud} has none. The sidebar is rebuilt only when the board
 * changes, not on every tick.
 */
@NotNullByDefault
public final class LobbySidebar {

    /** What a racer's sidebar shows, so an unchanged board is not sent again. */
    private record Shown(Sidebar sidebar, LobbyBoard board) {
    }

    private final Map<UUID, Shown> sidebars = new HashMap<>();

    /**
     * Shows the board to {@code racer} while the room waits in {@code waitingPlace}, and takes it down otherwise.
     *
     * @param racer        the racer the sidebar is for
     * @param waitingPlace the instance racers wait in
     * @param board        the board to show
     * @param waiting      whether the room is waiting, that is not racing
     * @return whether what the racer sees changed
     */
    public boolean update(Player racer, Instance waitingPlace, LobbyBoard board, boolean waiting) {
        UUID id = racer.getUuid();
        boolean wanted = waiting && racer.getInstance() == waitingPlace;
        if (!wanted) {
            return hide(racer);
        }
        Shown current = sidebars.get(id);
        if (current != null && current.board().equals(board)) {
            return false;
        }
        if (current != null) {
            current.sidebar().removeViewer(racer);
        }
        Sidebar sidebar = build(board);
        sidebar.addViewer(racer);
        sidebars.put(id, new Shown(sidebar, board));
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

    private static Sidebar build(LobbyBoard board) {
        Sidebar sidebar = new Sidebar(board.title());
        List<Component> lines = board.lines();
        for (int index = 0; index < lines.size(); index++) {
            // The first line is the highest score, so the lines read top to bottom in play order.
            sidebar.createLine(new Sidebar.ScoreboardLine("line-" + index, lines.get(index), lines.size() - index));
        }
        return sidebar;
    }
}
