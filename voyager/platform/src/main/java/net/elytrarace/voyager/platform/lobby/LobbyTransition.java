package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.NotNullByDefault;

/**
 * Holds the lobby world and its spawn, where a racer who joins the server waits.
 *
 * <p>The lobby world is resolved once, when the server is built, so a lobby folder that is not there is refused at boot
 * and not at the first join. A finished cup does not return racers here: they are handed to the network hub (ADR-0033).
 * A joining racer is placed at {@link #spawn()} by the join handler.
 *
 * <p>The move from the lobby to the first map is not here: it is the map transition's cross-instance branch, which a
 * racer leaving the lobby takes like any other move between worlds.
 */
@NotNullByDefault
public final class LobbyTransition {

    private final Instance instance;
    private final Pos spawn;

    /**
     * @param instances the world cache the lobby world is held in
     * @param lobby     the lobby the server is configured with
     * @throws net.elytrarace.voyager.platform.world.exception.UnknownWorldException if the lobby world has no region data
     */
    public LobbyTransition(MapInstances instances, LobbyDefinition lobby) {
        this.instance = instances.forWorld(lobby.world());
        this.spawn = Vectors.toMinestom(lobby.spawn()).asPos();
    }

    /** The lobby instance, the one a waiting racer stands in. */
    public Instance instance() {
        return instance;
    }

    /** Where a joining racer stands in the lobby. */
    public Pos spawn() {
        return spawn;
    }
}
