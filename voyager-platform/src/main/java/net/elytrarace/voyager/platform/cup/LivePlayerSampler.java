package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.tick.FlightSample;
import net.elytrarace.voyager.platform.tick.FlightSampler;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * The production {@link FlightSampler}: reads every online player at the tick boundary.
 *
 * <p>{@code FlightSample} is deliberately free of Minestom types so a fake can drive the whole tick
 * loop in a test, which puts the Minestom reads here, in the composition root, exactly as that
 * record's javadoc says. This class is that seam and nothing else — it decides nothing.
 *
 * <h2>The two conversions</h2>
 *
 * <p><strong>Velocity.</strong> Minestom carries an entity's velocity in blocks per <em>second</em>;
 * the domain is blocks per <em>tick</em>. This divides by the same
 * {@link ServerFlag#SERVER_TICKS_PER_SECOND} that {@code VelocityExit} multiplies by, so there is one
 * constant and two directions rather than two constants. It is not in {@code VelocityExit} because
 * that class is the one place a velocity is <em>sent</em> to Minestom, and an entry point in a class
 * named for the exit is a name that stops being true.
 *
 * <p>The value read back is not the value anything sent. {@code Entity.movementTick} runs for a
 * {@code Player} too and rewrites the simulated velocity every tick — measured at x0.91 per tick by
 * {@code MinestomVelocityAutoSyncTest} — so a velocity handed to Minestom is gone from the server's
 * own copy within about a second. That costs nothing here: {@code FlightSample}'s velocity is a seed,
 * read only on the tick a flight starts, and from then on {@code FlightTickDriver} holds the velocity
 * the game reasons about.
 *
 * <p><strong>Gravity.</strong> Vanilla's lift term is {@code gravity * (-1.0 + liftForce * 0.75)}
 * and gravity is read per tick, not assumed: Slow Falling clamps it to 0.01 while descending. The
 * reading is Minestom's per-entity {@code Aerodynamics#gravity()}, which is the same quantity the
 * E2a trace recorder read out of the entity's gravity attribute on a real server.
 * {@link FlightInput} refuses a non-positive gravity, so a player with gravity switched off entirely
 * would otherwise throw inside the tick; that case falls back to Vanilla's player default and says
 * so in the field, rather than taking down the loop.
 *
 * <h2>The one thing the boost reading cannot be exact about</h2>
 *
 * <p>The burn reported here is the <strong>server's</strong> window, counted by
 * {@link FireworkBoostTracker}: it opens on the tick after the use packet arrived and closes exactly
 * {@code burnDurationTicks} later. The client's window is not the same one. The client sees the
 * rocket appear and disappear through packets, so it begins boosting about half a round trip after
 * the server thinks it did and stops about half a round trip after the server stops — on a 60 ms
 * connection, roughly one tick of offset at each end out of thirty.
 *
 * <p>That drift is tolerable, and it is worth saying why rather than leaving a reader to work it out.
 * Ring collision is advanced with the client's <em>reported position</em>, not with the simulated one
 * ({@code CupSession.raceTick}), so nothing a player is scored on depends on the two windows lining
 * up. The simulation is a shadow kept for velocity and boost maths, and the offset shows up there as
 * a small divergence in a number that is already printed side by side with the client's in
 * {@code /race}. The day the simulation becomes the authority on anything — collision damage, an
 * out-of-bounds reset — this is the paragraph to come back to.
 */
public final class LivePlayerSampler implements FlightSampler {

    /** Vanilla's {@code minecraft:generic.gravity} default for a player, used only as a fallback. */
    private static final double VANILLA_PLAYER_GRAVITY = 0.08;

    private final Supplier<Collection<Player>> players;
    private final FireworkBoostTracker boosts;

    public LivePlayerSampler(Supplier<Collection<Player>> players, FireworkBoostTracker boosts) {
        this.players = players;
        this.boosts = boosts;
    }

    @Override
    public List<FlightSample> sampleAtTickBoundary() {
        Collection<Player> online = players.get();
        List<FlightSample> samples = new ArrayList<>(online.size());
        for (Player player : online) {
            samples.add(sample(player));
        }
        return List.copyOf(samples);
    }

    private FlightSample sample(Player player) {
        Pos position = player.getPosition();
        return new FlightSample(
                player.getUuid(),
                player.isFlyingWithElytra(),
                Vectors.toDomain(position),
                perTick(Vectors.toDomain(player.getVelocity())),
                player.isOnGround(),
                new FlightInput(
                        position.yaw(),
                        position.pitch(),
                        // Read per player, not per sample batch: a boost reported against the wrong
                        // racer would apply an impulse to a flight that never had one and withhold it
                        // from one that did, and both simulations would stay internally consistent
                        // while being wrong.
                        boosts.burning(player.getUuid()),
                        boosts.ticksRemaining(player.getUuid()),
                        gravityOf(player)));
    }

    private static Vec3 perTick(Vec3 perSecond) {
        return perSecond.scale(1.0 / ServerFlag.SERVER_TICKS_PER_SECOND);
    }

    private static double gravityOf(Player player) {
        double gravity = player.getAerodynamics().gravity();
        return Double.isFinite(gravity) && gravity > 0.0 ? gravity : VANILLA_PLAYER_GRAVITY;
    }
}
