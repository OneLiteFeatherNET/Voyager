package net.elytrarace.voyager.setup.adapter;

import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;

/**
 * Cancels a builder's block edits while the builder has a map open. The setup server never saves a world, so an edit
 * it allowed would vanish on restart without a word; the builder builds terrain in the external editor (research 005,
 * section 1.3). Edits outside an open map are not this class's business.
 */
public final class TerrainGuard {

    private final BuilderSessions sessions;

    public TerrainGuard(BuilderSessions sessions) {
        this.sessions = sessions;
    }

    /**
     * @param events the node that receives the server's events; the global handler in production
     */
    public void register(EventNode<Event> events) {
        events.addListener(PlayerBlockBreakEvent.class, event -> {
            if (isInOpenMap(event.getPlayer())) {
                event.setCancelled(true);
            }
        });
        events.addListener(PlayerStartDiggingEvent.class, event -> {
            if (isInOpenMap(event.getPlayer())) {
                event.setCancelled(true);
            }
        });
        events.addListener(PlayerBlockPlaceEvent.class, event -> {
            if (isInOpenMap(event.getPlayer())) {
                event.setCancelled(true);
            }
        });
    }

    private boolean isInOpenMap(Player builder) {
        return sessions.find(builder.getUuid())
                .map(session -> session.instance() == builder.getInstance())
                .orElse(false);
    }
}
