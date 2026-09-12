package net.elytrarace.voyager.platform.render;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.race.line.RacingLine;
import net.minestom.server.color.Color;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws one racer the stretch of racing line ahead of them, in particles.
 *
 * <h2>Per racer, not per world</h2>
 *
 * <p>Two racers on the same map are at different rings, so they need different stretches, and a
 * particle is addressed to a connection rather than to a world — which is what makes that possible at
 * all. Nothing here is broadcast: every packet goes to the one player it was computed for.
 *
 * <h2>What decides the stretch</h2>
 *
 * <p>From the ring the racer is heading for, forward, as far as the map's own
 * {@code lookAheadRings} reaches. Not from the ring behind them: the line between a racer and a ring
 * they have already passed is behind their head, and drawing it spends packets on the one part of the
 * course that cannot help. The consequence at the end of the course is deliberate — a racer flying at
 * the last ring has nothing ahead of it and so is shown nothing, because the line ends where the
 * course does.
 *
 * <h2>What one racer costs</h2>
 *
 * <p>A particle is a packet: {@code ParticlePacket} carries one position, so a stretch of {@code n}
 * points is {@code n} packets to that racer. On the committed course — 35 rings, 1588 blocks, so
 * about 47 blocks a gap — a two-ring stretch at a block a particle averages 97 packets, and the
 * widest pair of gaps on it is 198. Twenty of those a second per racer is most of what a race server
 * would send, and almost all of it would be redrawing particles the client still has: the vanilla
 * dust particle lives for {@code 8 / (random * 0.8 + 0.2)} ticks, so the shortest-lived one on screen
 * lasts 8 ticks. Hence {@link #REFRESH_INTERVAL_TICKS} — the line is redrawn at 5 Hz, comfortably
 * inside the life of the particles already drawn, and one racer costs about 24 particles a tick
 * averaged rather than 97.
 */
public final class GuideLineRenderer {

    /**
     * How many ticks between two redraws of the line.
     *
     * <p>Four, from the client's own particle lifetime rather than from taste: the shortest a dust
     * particle can live is 8 ticks, so redrawing every 4 cannot leave a gap in the line, and anything
     * shorter is sending a packet for a particle that is already on screen. It is not a knob in the
     * map file — how often the server refreshes is a budget decision about the server, not a
     * statement about the course, and a designer editing a map has nothing to say about it.
     */
    public static final int REFRESH_INTERVAL_TICKS = 4;

    /**
     * The line's colour: the amber the tree being replaced used for its partial-line mode, which is
     * the mode this is. A constant rather than map data — a racer learns one colour means "the way
     * through", and a course that disagreed with the next course about it would unteach that.
     */
    private static final Color COLOR = new Color(255, 200, 0);

    /** Dust scale. One is vanilla's own size; the tree being replaced used 1.0 for this mode too. */
    private static final float SCALE = 1.0f;

    private static final Particle DUST = Particle.DUST.withProperties(COLOR, SCALE);

    /**
     * Both flags on, and both matter for a line seen from a distance at speed. One lifts the client's
     * 32-block cull, without which a racer would see only the nearest third of a stretch that is there
     * to show them what is coming; the other draws the line even for a client set to reduced
     * particles, because this one is not decoration — a racer who cannot see it cannot find the course.
     */
    private static final boolean LONG_DISTANCE = true;

    private static final boolean OVERRIDE_LIMITER = true;

    /** A dust particle needs no spread and no speed: one particle, exactly where it was put. */
    private static final Vec NO_SPREAD = Vec.ZERO;

    private static final float NO_SPEED = 0.0f;

    private static final int ONE_PARTICLE = 1;

    /**
     * One sampled line per map, by map name.
     *
     * <p>Sampling the committed course produces about seventeen hundred points and walks every
     * segment twice to get there; doing that once per racer per tick is the difference between this
     * feature being affordable and not. A course's line never changes — it is a function of ring
     * centres, guide points and a spacing, all of which are read once off disk — so an entry is
     * computed on first use and kept for the life of the server.
     */
    private final Map<String, RacingLine> linesByMap = new HashMap<>();

    /**
     * Draws the racer their stretch of the line, if this tick is one the line is redrawn on.
     *
     * @param racer who to send the particles to
     * @param map the course being raced, which carries both the line's shape and how much of it to show
     * @param passedCount how many rings this racer has passed, so the ring they are heading for is
     *     {@code rings.get(passedCount)} — the same index a {@code ProgressTracker} reads by
     * @param gameTick the race clock's tick, which is what the refresh interval is counted in
     */
    public void render(Player racer, MapDefinition map, int passedCount, long gameTick) {
        if (!refreshesOn(gameTick)) {
            return;
        }
        RacingLine line = linesByMap.computeIfAbsent(map.name(), name -> RacingLine.of(map));
        List<Vec3> stretch = stretch(line, passedCount, map.guideLine().lookAheadRings());
        if (stretch.size() < 2) {
            return;
        }
        List<SendablePacket> packets = new ArrayList<>(stretch.size());
        for (Vec3 point : stretch) {
            packets.add(new ParticlePacket(DUST, OVERRIDE_LIMITER, LONG_DISTANCE,
                    Vectors.toMinestom(point), NO_SPREAD, NO_SPEED, ONE_PARTICLE));
        }
        racer.sendPackets(packets);
    }

    /**
     * The stretch of line a racer at {@code passedCount} rings is shown.
     *
     * <p>Package-private because it is this class's policy and nothing outside needs it yet, and
     * because it is the half of this class a test can reach: which points go out is decidable with no
     * server anywhere, and whether they arrive is not. {@code GuideLineRendererTest} is written
     * against it.
     *
     * @return the points, in flight order; empty once every ring has been passed, and a single point
     *     when the racer is heading for the last ring, which has nothing after it
     */
    static List<Vec3> stretch(RacingLine line, int passedCount, int lookAheadRings) {
        if (passedCount < 0 || passedCount >= line.ringCount()) {
            return List.of();
        }
        int lastRing = Math.min(passedCount + lookAheadRings, line.ringCount() - 1);
        return line.between(passedCount, lastRing);
    }

    /** Whether the line is redrawn on this tick. See {@link #REFRESH_INTERVAL_TICKS}. */
    static boolean refreshesOn(long gameTick) {
        return gameTick % REFRESH_INTERVAL_TICKS == 0;
    }
}
