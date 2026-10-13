package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.exception.InvalidLobbyException;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/**
 * The world waiting racers stand in, and the spot in it they stand on.
 *
 * <p>The lobby is a deployment fact, not catalogue content: one lobby per server, named by the server's settings and
 * never by a map or a cup. Its world is a world directory name, as a map's world is, and its spawn is a position in
 * that world.
 *
 * @param world the name of the world directory the lobby is loaded from; not blank
 * @param spawn where a waiting racer stands
 */
@NotNullByDefault
public record LobbyDefinition(String world, Vec3 spawn) {

    public LobbyDefinition {
        Objects.requireNonNull(world, "the lobby world name must not be null");
        Objects.requireNonNull(spawn, "the lobby spawn must not be null");
        if (world.isBlank()) {
            throw InvalidLobbyException.blankWorld(world);
        }
    }
}
