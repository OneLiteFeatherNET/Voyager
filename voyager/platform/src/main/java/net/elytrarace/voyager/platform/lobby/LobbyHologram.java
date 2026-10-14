package net.elytrarace.voyager.platform.lobby;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Optional;

/**
 * The cup as a text display that stands at a fixed position in the lobby world (ADR-0033).
 *
 * <p>One entity, spawned once when the server boots, and never saved with the lobby world: the lobby is not written on stop,
 * so the entity is spawned again on every boot. Its text is the same board the sidebar shows, and it changes only when the
 * board does.
 */
@NotNullByDefault
public final class LobbyHologram {

    private final Entity entity;
    private LobbyBoard shown;

    private LobbyHologram(Entity entity, LobbyBoard shown) {
        this.entity = entity;
        this.shown = shown;
    }

    /**
     * Spawns the hologram at {@code position} in {@code lobby}, if a position is configured.
     *
     * @param position the position, or empty when the server has no hologram
     * @param lobby    the lobby instance the hologram stands in
     * @param board    the board the hologram first shows
     * @return the hologram, or empty when no position is configured, in which case nothing is spawned
     */
    public static Optional<LobbyHologram> spawnAt(Optional<Pos> position, Instance lobby, LobbyBoard board) {
        return position.map(at -> {
            Entity display = new Entity(EntityType.TEXT_DISPLAY);
            display.setNoGravity(true);
            ((TextDisplayMeta) display.getEntityMeta()).setText(text(board));
            display.setInstance(lobby, at).join();
            return new LobbyHologram(display, board);
        });
    }

    /**
     * Shows the board's text, if the board has changed since the last update.
     *
     * @param board the board the hologram shows from now on
     */
    public void update(LobbyBoard board) {
        if (board.equals(shown)) {
            return;
        }
        ((TextDisplayMeta) entity.getEntityMeta()).setText(text(board));
        shown = board;
    }

    /**
     * The entity the hologram is. Package-private, for {@code LobbyHologramTest}: the position and the instance are read from it.
     */
    Entity entity() {
        return entity;
    }

    /** The board as the hologram reads it: the cup's name on the first line, then the maps, one line each. */
    static Component text(LobbyBoard board) {
        Component text = board.title();
        for (Component line : board.lines()) {
            text = text.append(Component.newline()).append(line);
        }
        return text;
    }
}
