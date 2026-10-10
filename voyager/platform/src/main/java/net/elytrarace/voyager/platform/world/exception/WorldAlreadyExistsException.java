package net.elytrarace.voyager.platform.world.exception;

import java.nio.file.Path;

/** Thrown when a void world would be copied over a world folder that already exists. */
public final class WorldAlreadyExistsException extends RuntimeException {

    private WorldAlreadyExistsException(String message) {
        super(message);
    }

    public static WorldAlreadyExistsException at(Path world) {
        return new WorldAlreadyExistsException("the world folder %s already exists; a new map never replaces a world"
                .formatted(world));
    }
}
