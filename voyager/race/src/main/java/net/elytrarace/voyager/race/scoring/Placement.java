package net.elytrarace.voyager.race.scoring;

/**
 * One competitor's {@link MapScore} on a map, tagged with whatever the caller identifies that
 * competitor by.
 *
 * <p>The key is a type parameter rather than a player type on purpose: {@code voyager-race} has no
 * concept of a player and is not going to grow one — a platform stage keys by {@code UUID}, a test
 * by a name, a replay by a row id. What the type buys is that {@link PlacementBonus#award} hands
 * each bonus back attached to the competitor it belongs to, instead of returning a list the caller
 * has to keep index-aligned with a parallel list of its own.
 *
 * @param key   what the caller calls this competitor
 * @param score that competitor's score on the map being ranked
 * @param <K>   the caller's identifier type
 */
public record Placement<K>(K key, MapScore score) {
}
