package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.FlightInput;

import java.util.UUID;

/**
 * One player as observed at a tick boundary, <strong>before</strong> the tick this sample drives.
 *
 * <p>{@code position}, {@code velocity} and {@code onGround} seed a flight that starts on this tick
 * and are ignored on every later tick of the same flight: normal elytra flight is
 * client-authoritative, and re-seeding from the observation each tick would leave the server's own
 * simulation with nothing to simulate. {@code input} is applied on <em>every</em> tick, this one
 * included — see {@link FlightTickDriver#tick()} for why applying it one tick later is the failure
 * mode that cannot be detected downstream.
 *
 * <p>Deliberately free of Minestom types, exactly as {@code FlightTracker} is: a fake source drives
 * the whole loop in a test, and the Minestom reads — {@code Player#getPosition},
 * {@code isFlyingWithElytra()}, {@code Vectors.toDomain} — stay in the composition root that builds
 * these.
 *
 * @param playerId whose flight this is
 * @param flyingWithElytra this tick's {@code EntityMeta.isFlyingWithElytra()} reading
 * @param position the observed position at this tick boundary; a seed only
 * @param velocity the observed velocity at this tick boundary, in blocks per tick; a seed only
 * @param onGround the observed ground flag at this tick boundary; a seed only
 * @param input the rotation and gravity the player carries <em>into</em> this tick
 */
public record FlightSample(
        UUID playerId,
        boolean flyingWithElytra,
        Vec3 position,
        Vec3 velocity,
        boolean onGround,
        FlightInput input) {
}
