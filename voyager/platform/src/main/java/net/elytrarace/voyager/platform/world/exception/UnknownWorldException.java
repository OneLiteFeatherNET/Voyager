package net.elytrarace.voyager.platform.world.exception;

import java.nio.file.Path;

/**
 * Thrown when a map names a world directory that holds no region data.
 *
 * <p>This is the failure that has to be loud. A world loader handed a name nothing is behind returns
 * no chunks, the server generates fresh ones in their place, and the result is a perfectly
 * functional void world that a player can be teleported into and fly around in. A typo in a map
 * definition and a deleted world are the same thing from the inside; both have to stop startup here
 * rather than turn into an empty racetrack.
 *
 * <p>The message carries the path the loader resolved rather than the world root it was given,
 * because the two differ: the loader picks either the dimension layout or the legacy one depending
 * on what exists, so "looked in the wrong place" and "the place is empty" are not the same report.
 */
public final class UnknownWorldException extends RuntimeException {

    public UnknownWorldException(String world, Path regionDirectory, boolean legacyLayout) {
        super("world '%s' holds no region data; the loader resolved %s using the %s layout"
                .formatted(world, regionDirectory, legacyLayout ? "legacy" : "dimension"));
    }
}
