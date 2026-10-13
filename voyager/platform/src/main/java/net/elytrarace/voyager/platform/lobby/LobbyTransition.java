package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.api.race.LobbyDefinition;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.world.Arrivals;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Collection;

/**
 * Holds the lobby world and its spawn, and brings racers back to it when a cup finishes.
 *
 * <p>The lobby world is resolved once, when the server is built, so a lobby folder that is not there is refused at boot
 * and not at the first join. The move back is the shared arrival step ({@link Arrivals}), the same one a map move
 * uses. A racer who is already in the lobby is repositioned on the spawn within it.
 *
 * <p>The move from the lobby to the first map is not here: it is the map transition's cross-instance branch, which a
 * racer leaving the lobby takes like any other move between worlds.
 */
@NotNullByDefault
public final class LobbyTransition {

    private final String world;
    private final Instance instance;
    private final Pos spawn;

    /**
     * @param instances the world cache the lobby world is held in
     * @param lobby     the lobby the server is configured with
     * @throws net.elytrarace.voyager.platform.world.exception.UnknownWorldException if the lobby world has no region data
     */
    public LobbyTransition(MapInstances instances, LobbyDefinition lobby) {
        this.world = lobby.world();
        this.instance = instances.forWorld(lobby.world());
        this.spawn = Vectors.toMinestom(lobby.spawn()).asPos();
    }

    /** The lobby instance, the one a waiting racer stands in. */
    public Instance instance() {
        return instance;
    }

    /** Where a waiting racer stands in the lobby. */
    public Pos spawn() {
        return spawn;
    }

    /**
     * Moves every racer to the lobby spawn. Returns once each has arrived, as {@link Arrivals#arrive} does.
     *
     * @param racers the racers to bring back
     * @throws net.elytrarace.voyager.platform.world.exception.ArrivalFailedException if a chunk around the spawn fails
     *     to load; the racer is not moved
     */
    public void returnTo(Collection<Player> racers) {
        for (Player racer : racers) {
            Arrivals.arrive(racer, world, instance, spawn);
        }
    }
}
