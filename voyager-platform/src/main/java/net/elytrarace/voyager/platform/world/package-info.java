/**
 * Loading the worlds a racetrack lives in, reporting whether they really loaded, and moving the
 * racers from one map to the next.
 *
 * <p>The per-player run state sits here too, next to the transition that empties and refills it:
 * that adjacency is what lets {@code RaceRuns} keep "a run starts" and "a run is dropped" as
 * package-private operations, so a run cannot exist for a player who is not standing on the map it
 * is over.
 */
@NotNullByDefault
package net.elytrarace.voyager.platform.world;

import org.jetbrains.annotations.NotNullByDefault;
