package net.elytrarace.voyager.platform.convert;

import net.elytrarace.voyager.api.math.Vec3;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;

/**
 * <strong>A measurement, kept as a test.</strong> Minestom issue
 * <a href="https://github.com/Minestom/Minestom/issues/1880">#1880</a> — an auto-sync tick that
 * resets velocity, with <a href="https://github.com/Minestom/Minestom/issues/2267">#2267</a> closed
 * as a duplicate of it — is still open, and the rebuild has carried an inference about it for two
 * stages: that it cannot reach a player at all, because the line in question is
 * {@code sendPacketToViewers} and a {@link Player} is not its own viewer. This class replaces that
 * inference with an observation on a running server, and keeps the answer from drifting.
 *
 * <h2>What the scheduled sync is</h2>
 *
 * <p>{@code Entity.tick} ends with a scheduled block: once {@code ticks >= nextSynchronizationTick}
 * — every {@code ServerFlag.ENTITY_SYNCHRONIZATION_TICKS} ticks, 20 by default — it calls
 * {@code synchronizePosition()} and {@code sendPacketToViewers(getVelocityPacket())}. The question
 * this class answers is who receives that velocity packet, and what it does to the sender.
 *
 * <h2>What was measured, on {@code net.minestom:minestom:2026.08.28-26.2}</h2>
 *
 * <p>A racer given {@code (0.75, -0.32, 1.6)} blocks per tick through {@link VelocityExit} —
 * {@code (15.0, -6.4, 32.0)} blocks per second on the entity — with a second player standing four
 * blocks away as a viewer, and 25 server ticks played over it:
 *
 * <ul>
 *   <li>The racer's own connection received <strong>zero</strong> {@code EntityVelocityPacket}s
 *       describing the racer. It received exactly one describing the <em>viewer</em>, which is that
 *       player's own scheduled sync arriving at a viewer — the packets are going where the source
 *       says and the racer is simply not on its own viewer list.</li>
 *   <li>The viewer's connection received exactly one describing the racer, at the racer's 20th tick,
 *       carrying {@code (0.12498207119519993, -1.4675625527363876, 0.2666284185497597)} — the
 *       racer's server-side velocity of that tick divided by the 20 TPS the packet is scaled by.</li>
 *   <li>The racer's server-side velocity was not reset by that tick. It decayed by a constant
 *       {@code x 0.91} per tick horizontally, continuously across the sync boundary:
 *       {@code 2.4996414239039986 -> 2.2746736957526386} on the sync tick itself, the same factor as
 *       every tick before and after it.</li>
 * </ul>
 *
 * <p><strong>So #1880 does not reach a racing player's client in 26.2, and needs no workaround.</strong>
 *
 * <h2>The finding that is worth carrying instead</h2>
 *
 * <p>The decay above is not the sync; it is {@code Entity.movementTick}, which runs for a
 * {@link Player} too and writes {@code velocity = PhysicsUtils.simulateMovement(...).newVelocity()}
 * back every single tick. A velocity handed to Minestom is therefore gone from the server's own
 * copy within a second, whatever the sync does. That costs the rebuild nothing — normal elytra
 * flight is client-authoritative and {@code FlightTickDriver} holds the velocity the game reasons
 * about — but anything that reads {@code Player#getVelocity()} back expecting to find what it sent
 * is reading a decayed value, not the one it wrote.
 */
@EnvTest
class MinestomVelocityAutoSyncTest {

    /**
     * Mutually distinct, not all positive, and no component equal to the tick rate — the same
     * reasoning as {@code VelocityExitTest}'s fixture, so a factor cannot be mistaken for a
     * component swap and a lost sign shows up.
     */
    private static final Vec3 BOOST_PER_TICK = new Vec3(0.75, -0.32, 1.6);

    /** Enough ticks to cross one synchronization interval and no more, so exactly one sync lands. */
    private static final int TICKS_PAST_THE_INTERVAL = 3;

    @Test
    void theScheduledSyncSendsNoVelocityPacketToThePlayerItDescribes(Env env) {
        Measurement measurement = measure(env);

        assertThat(measurement.toTheViewer())
                .describedAs("positive control: the scheduled sync did fire inside the measured window")
                .hasSize(1);
        assertThat(measurement.toTheRacer())
                .describedAs("the racer is not on its own viewer list, so nothing about the racer "
                        + "reaches the racer — #1880 cannot touch a client's own velocity")
                .isEmpty();
    }

    /**
     * The other half of the question: the sync could in principle have reset the velocity on the way
     * out. It does not. The horizontal decay is a constant multiply, so the tick the sync lands on is
     * predicted exactly by the tick before it — asserted for every tick of the window rather than for
     * one hand-picked boundary, so this does not depend on knowing which tick the sync chose.
     */
    @Test
    void theScheduledSyncDoesNotResetTheSendersOwnVelocity(Env env) {
        Measurement measurement = measure(env);
        List<Vec> velocities = measurement.velocityPerTick();

        assertThat(measurement.toTheViewer())
                .describedAs("positive control: the scheduled sync did fire inside the measured window")
                .hasSize(1);

        double decay = velocities.get(1).x() / velocities.getFirst().x();
        assertThat(decay)
                .describedAs("a window in which the velocity neither stands still nor vanishes, or "
                        + "there would be nothing for a reset to interrupt")
                .isStrictlyBetween(0.0, 1.0);

        for (int tick = 1; tick < velocities.size(); tick++) {
            Vec previous = velocities.get(tick - 1);
            Vec current = velocities.get(tick);
            assertThat(current.x())
                    .describedAs("x on tick %d of the window", tick)
                    .isCloseTo(previous.x() * decay, withPrecision(1.0e-12));
            assertThat(current.z())
                    .describedAs("z on tick %d of the window", tick)
                    .isCloseTo(previous.z() * decay, withPrecision(1.0e-12));
        }
    }

    /**
     * One racer, one viewer standing beside them, a velocity sent, and one synchronization interval
     * played out. The viewer is what makes the measurement falsifiable: without a second player
     * there are no viewers at all, and "no packet arrived" would be indistinguishable from "no sync
     * happened".
     */
    private static Measurement measure(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        TestConnection racerConnection = env.createConnection();
        Player racer = racerConnection.connect(instance, new Pos(0, 60, 0));
        TestConnection viewerConnection = env.createConnection();
        Player viewer = viewerConnection.connect(instance, new Pos(4, 60, 4));
        env.tick();

        VelocityExit.send(racer, BOOST_PER_TICK);

        Collector<EntityVelocityPacket> toRacer = racerConnection.trackIncoming(EntityVelocityPacket.class);
        Collector<EntityVelocityPacket> toViewer = viewerConnection.trackIncoming(EntityVelocityPacket.class);
        List<Vec> velocities = new ArrayList<>();
        velocities.add(racer.getVelocity());

        for (int tick = 0; tick < racer.getSynchronizationTicks() + TICKS_PAST_THE_INTERVAL; tick++) {
            env.tick();
            velocities.add(racer.getVelocity());
        }

        assertThat(racer.getViewers().contains(viewer))
                .describedAs("the viewer really is a viewer of the racer, and the racer is not")
                .isTrue();
        assertThat(racer.getViewers().contains(racer)).isFalse();
        return new Measurement(List.copyOf(velocities), about(racer, toRacer), about(racer, toViewer));
    }

    /** Only the packets describing the racer: a viewer's own scheduled sync is not the subject. */
    private static List<EntityVelocityPacket> about(Player racer, Collector<EntityVelocityPacket> collector) {
        return collector.collect().stream().filter(packet -> packet.entityId() == racer.getEntityId()).toList();
    }

    private record Measurement(List<Vec> velocityPerTick, List<EntityVelocityPacket> toTheRacer,
                               List<EntityVelocityPacket> toTheViewer) {
    }
}
