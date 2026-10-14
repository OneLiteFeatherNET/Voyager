package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;

import java.util.UUID;

/**
 * One computer racer's state on one tick, as a {@link CompetitorField} reports it to the cup.
 *
 * @param id the competitor's identity, the same id the field returned from its {@code prepare}
 * @param position where the competitor is on this tick
 * @param gliding whether the competitor is gliding on this tick
 */
public record Competitor(UUID id, Vec3 position, boolean gliding) {
}
