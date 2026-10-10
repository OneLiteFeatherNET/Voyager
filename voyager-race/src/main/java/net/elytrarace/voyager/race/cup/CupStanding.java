package net.elytrarace.voyager.race.cup;

import net.elytrarace.voyager.race.scoring.CupScore;

import java.util.UUID;

/**
 * One racer's standing across a whole cup: who they are and what they scored.
 *
 * <p>{@code Placement<K>} already pairs a competitor with a score, but with a {@code MapScore} — a
 * single map's three components. A cup total has no medal and no placement component of its own, so
 * carrying one in a {@code MapScore} would mean filling two fields with zeroes and a medal tier with
 * a lie. This is the same pairing one layer up.
 *
 * @param playerId whose standing this is
 * @param score their cup total
 */
public record CupStanding(UUID playerId, CupScore score) {
}
