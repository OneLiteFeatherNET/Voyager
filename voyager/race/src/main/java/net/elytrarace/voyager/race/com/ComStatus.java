package net.elytrarace.voyager.race.com;

import org.jetbrains.annotations.ApiStatus;

/** Where a computer racer stands in its map: still racing, finished the course, or removed as did-not-finish. */
@ApiStatus.Internal
public enum ComStatus {
    RACING,
    FINISHED,
    DID_NOT_FINISH
}
