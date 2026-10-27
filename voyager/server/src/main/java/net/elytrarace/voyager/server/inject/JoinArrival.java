package net.elytrarace.voyager.server.inject;

import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.flight.ArrivalFacing;
import net.elytrarace.voyager.platform.lobby.LobbyTransition;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Optional;

/**
 * Where a joining racer is first put, and where they respawn: the lobby when one is configured, and the first map's
 * spawn when not, as before ADR-0029.
 *
 * <p>Lives in the composition root's package because it names Minestom's instance and position, which only the
 * composition root may name; {@code VoyagerServer} asks it when a racer's configuration is decided.
 */
@NotNullByDefault
public final class JoinArrival {

    private JoinArrival() {
    }

    /** An instance, and a position in it. */
    public record Arrival(Instance instance, Pos position) {
    }

    /**
     * Where a joining racer spawns. The lobby is resolved already, when the graph was built, so this loads no world.
     *
     * @param lobby     the lobby the waiting room holds, or empty when none is configured
     * @param first     the first map of the current catalogue
     * @param instances the world cache the first map's world is resolved through
     * @return the instance and position a joining racer is spawned at, and respawns at
     */
    public static Arrival of(Optional<LobbyTransition> lobby, MapDefinition first, MapInstances instances) {
        return lobby
                .map(transition -> new Arrival(transition.instance(), transition.spawn()))
                .orElseGet(() -> new Arrival(instances.forWorld(first.world()), ArrivalFacing.at(first)));
    }
}
