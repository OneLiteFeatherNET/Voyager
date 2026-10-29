package net.elytrarace.voyager.setup.adapter;

import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.setup.mapsetup.EditClaims;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The open map of each builder, and the claim that goes with it. One builder has one open map; opening another replaces
 * the session, and the previous draft is already saved, because every change is saved when it is made.
 *
 * <p>The claim lifecycle lives here, because the events that end a session are handled here too: a replacement by
 * {@link #open}, a {@link #forget} by close, and the disconnect. No path can leave a claim behind a session that ended.
 */
public final class BuilderSessions {

    private final Map<UUID, MapSession> sessions = new HashMap<>();
    private final EditClaims claims;

    /**
     * @param claims the registry of the setup server's claims, shared by every builder
     */
    public BuilderSessions(EditClaims claims) {
        this.claims = claims;
    }

    /**
     * Ends a builder's session and releases every claim the builder holds when the builder disconnects, so that the
     * previews of the map do not stay in its world.
     *
     * @param events the node that receives the server's events; the global handler in production
     */
    public void register(EventNode<Event> events) {
        events.addListener(PlayerDisconnectEvent.class, event -> {
            UUID builder = event.getPlayer().getUuid();
            forget(builder);
            claims.releaseAll(builder);
        });
    }

    public Optional<MapSession> find(UUID builder) {
        return Optional.ofNullable(sessions.get(builder));
    }

    /**
     * Grants the builder the claim of a map, unless another builder holds it. Nothing else changes; the caller opens the
     * session once the claim is granted.
     *
     * @param builder the builder who asks
     * @param name    the builder's name, recorded with the claim
     * @param map     the map to claim
     * @return the holder who refused the claim, or empty when the claim is granted
     */
    public Optional<EditClaims.Holder> claim(UUID builder, String name, MapId map) {
        return claims.claim(key(map), builder, name);
    }

    /**
     * Opens a session for the builder, closing the one it replaces. The claim of the replaced map is released after the
     * claim of the new one has been granted, so a refused open never leaves the builder without a map. The builder must
     * already hold the claim of the session's map.
     *
     * @param builder the builder
     * @param session the session of the map the builder now edits
     */
    public void open(UUID builder, MapSession session) {
        MapSession previous = sessions.put(builder, session);
        if (previous != null) {
            previous.close();
            if (!previous.id().equals(session.id())) {
                claims.release(key(previous.id()), builder);
            }
        }
    }

    /**
     * Ends a builder's session and releases the claim of its map. A builder without a session changes nothing.
     *
     * @param builder the builder
     */
    public void forget(UUID builder) {
        MapSession previous = sessions.remove(builder);
        if (previous != null) {
            previous.close();
            claims.release(key(previous.id()), builder);
        }
    }

    /**
     * Releases the claim of a map whoever holds it, and ends the holder's session on that map. Only an unlock by a sender
     * with {@code voyager.setup.unlock} calls this.
     *
     * @param map     the map to unlock
     * @param onEnded called with the holder's player after the holder's session on the map has ended
     * @return the holder that was released, or empty when no builder held the map
     */
    public Optional<EditClaims.Holder> unlock(MapId map, Consumer<Player> onEnded) {
        Optional<EditClaims.Holder> released = claims.unlock(key(map));
        released.ifPresent(holder -> {
            MapSession session = sessions.get(holder.builder());
            if (session != null && session.id().equals(map)) {
                sessions.remove(holder.builder());
                session.close();
                onEnded.accept(session.builder());
            }
        });
        return released;
    }

    private static String key(MapId map) {
        return "map:" + map.value();
    }
}
