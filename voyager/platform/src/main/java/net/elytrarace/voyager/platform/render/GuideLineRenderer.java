package net.elytrarace.voyager.platform.render;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideLineMode;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.race.line.RacingLine;
import net.minestom.server.color.Color;
import net.minestom.server.coordinate.Point;
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
 * Draws one racer the racing line of the map they are on, in particles.
 *
 * <h2>Per racer, not per world</h2>
 *
 * <p>Two racers on the same map are at different rings, so they need different stretches, and a
 * particle is addressed to a connection rather than to a world — which is what makes that possible at
 * all. Nothing here is broadcast: every packet goes to the one player it was computed for.
 *
 * <h2>What decides the stretch</h2>
 *
 * <p>The map's {@code mode} decides it, and both modes start at the last ring the racer passed, one ring
 * behind the ring they are heading for, so the segment they are flying stays drawn until the next ring is
 * passed. {@code FULL}, the default, runs from there to the last ring: the whole course ahead, as the old game
 * drew it. {@code WINDOW} runs from there {@code lookAheadRings} rings on, for a course where the whole line is
 * too much. The line behind the last ring passed is not drawn in either mode. ADR-0030 chose the whole course
 * and reversed the old argument that a ball of string does not say which strand is next: a racer plans from
 * the strands ahead of them, and the flown part was the one part that could not help.
 *
 * <h2>What the particle, the colour and the refresh are</h2>
 *
 * <p>The particle and the colour are the map's, with one default every course shares: END_ROD, or DUST in
 * {@code dustColor}. The refresh is the particle's, not the map's: how often the server redraws is a budget
 * decision about the server, and it is set by the particle's shortest life so that the line shows no gap
 * between two redraws. The owner's proposed refresh for END_ROD was ten ticks, but its shortest life
 * could not be read from a source this change could reach, so six ticks is used. design.md D4 records
 * the choice.
 *
 * <h2>What one racer costs</h2>
 *
 * <p>A particle is a packet: {@code ParticlePacket} carries one position, so a stretch of {@code n} points
 * is {@code n} packets to that racer each time it is redrawn. The reach cut-off in
 * {@link #REACH_BLOCKS} is what keeps that inside the owner's budget of 30,000 packets a second in total and
 * 2,000 for one racer: a racer is sent only the points within reach of them, so the cost follows the local
 * density of the course, not its length.
 */
public final class GuideLineRenderer {

    /**
     * How far from the racer a point of the line may be and still be sent, in blocks.
     *
     * <p>160 sits below the 256-block long-distance draw the renderer requests ({@link #LONG_DISTANCE}), so
     * no sent particle is out of the client's range, and it leaves a margin for a racer who moves between
     * redraws. It is a server constant and not a map field: it bounds the packets by the local density of
     * the course, which is the thing the budget is about.
     */
    static final double REACH_BLOCKS = 160.0;

    /**
     * Ticks between two redraws of an END_ROD line.
     *
     * <p>Six, not the owner's proposed ten. END_ROD's shortest particle life could not be read from a
     * source the change could reach, so the safe value is used: six is at most half of any life of twelve
     * ticks or more. If the life is read later to be at least twenty ticks, ten is the value the owner
     * proposed and the budget allows it. design.md D4 and D6 record both figures.
     */
    static final int END_ROD_REFRESH_TICKS = 6;

    /**
     * Ticks between two redraws of a DUST line. Four, from the vanilla dust particle's shortest life of eight
     * ticks (vanilla {@code 8 / (random * 0.8 + 0.2)}): one redraw always lands before the previous points go.
     */
    static final int DUST_REFRESH_TICKS = 4;

    /**
     * The look-ahead FULL mode uses: far more rings than any course has, so the stretch is clamped to the
     * last ring. Half of the maximum int, so that the racer's ring plus it cannot overflow.
     */
    private static final int FULL_LOOK_AHEAD = Integer.MAX_VALUE / 2;

    /** Dust scale. One is vanilla's own size. */
    private static final float DUST_SCALE = 1.0f;

    /**
     * Both flags on, and both matter for a line seen from a distance at speed. One lifts the client's
     * 32-block cull, without which a racer would see only the nearest third of a stretch that is there to
     * show them what is coming; the other draws the line even for a client set to reduced particles, because
     * this one is not decoration — a racer who cannot see it cannot find the course.
     */
    private static final boolean LONG_DISTANCE = true;

    private static final boolean OVERRIDE_LIMITER = true;

    /** A particle needs no spread and no speed: one particle, exactly where it was put. */
    private static final Vec NO_SPREAD = Vec.ZERO;

    private static final float NO_SPEED = 0.0f;

    private static final int ONE_PARTICLE = 1;

    /**
     * One sampled line per map, by map name.
     *
     * <p>Sampling the committed course produces about seventeen hundred points and walks every segment
     * twice to get there; doing that once per racer per tick is the difference between this feature being
     * affordable and not. A course's line never changes — it is a function of ring centres, guide points and
     * a spacing, all of which are read once off disk — so an entry is computed on first use and kept for the
     * life of the server.
     */
    private final Map<String, RacingLine> linesByMap = new HashMap<>();

    /**
     * Draws the racer their stretch of the line, if this tick is one the line is redrawn on.
     *
     * @param racer who to send the particles to; only the points within {@link #REACH_BLOCKS} of them are sent
     * @param map the course being raced, which carries the line's shape and its style
     * @param passedCount how many rings this racer has passed, so the ring they are heading for is
     *     {@code rings.get(passedCount)} — the same index a {@code ProgressTracker} reads by
     * @param gameTick the race clock's tick, which is what the refresh interval is counted in
     */
    public void render(Player racer, MapDefinition map, int passedCount, long gameTick) {
        GuideLine style = map.guideLine();
        if (!refreshesOn(gameTick, refreshTicksOf(style.particle()))) {
            return;
        }
        RacingLine line = linesByMap.computeIfAbsent(map.name(), name -> RacingLine.of(map));
        List<Vec3> stretch = stretch(line, passedCount, lookAheadOf(style));
        if (stretch.size() < 2) {
            return;
        }
        Point position = racer.getPosition();
        Vec3 racerAt = new Vec3(position.x(), position.y(), position.z());
        List<SendablePacket> packets = new ArrayList<>(stretch.size());
        for (Vec3 point : stretch) {
            if (withinReach(point, racerAt)) {
                packets.add(packetAt(point, style));
            }
        }
        if (!packets.isEmpty()) {
            racer.sendPackets(packets);
        }
    }

    /**
     * How many rings past the ring a racer is heading for the line reaches under a style. FULL reaches past
     * any course, so it runs to the last ring; WINDOW reaches {@code lookAheadRings}.
     */
    static int lookAheadOf(GuideLine style) {
        return style.mode() == GuideLineMode.FULL ? FULL_LOOK_AHEAD : style.lookAheadRings();
    }

    /**
     * The stretch of line a racer at {@code passedCount} rings is shown: from the last ring passed, through the
     * ring they are heading for, reaching {@code lookAheadRings} rings on and clamped to the last ring.
     *
     * <p>The stretch starts one ring behind the ring being headed for, so the segment the racer is flying stays
     * drawn until the next ring is passed. Before the first ring is passed it starts at the first ring.
     *
     * <p>Package-private because it is this class's policy and nothing outside needs it, and because it is
     * the half of this class a test can reach: which points go out is decidable with no server anywhere.
     *
     * @return the points, in flight order; empty once every ring has been passed, and a single point when
     *     the racer is heading for the last ring, which has nothing after it
     */
    static List<Vec3> stretch(RacingLine line, int passedCount, int lookAheadRings) {
        if (passedCount < 0 || passedCount >= line.ringCount()) {
            return List.of();
        }
        int firstRing = Math.max(passedCount - 1, 0);
        int lastRing = Math.min(passedCount + lookAheadRings, line.ringCount() - 1);
        return line.between(firstRing, lastRing);
    }

    /** Whether a point is close enough to a racer to be sent to them. See {@link #REACH_BLOCKS}. */
    static boolean withinReach(Vec3 point, Vec3 racerAt) {
        return point.distanceTo(racerAt) <= REACH_BLOCKS;
    }

    /** The refresh interval of a particle, in ticks. See {@link #END_ROD_REFRESH_TICKS}. */
    static int refreshTicksOf(GuideParticle particle) {
        return switch (particle) {
            case END_ROD -> END_ROD_REFRESH_TICKS;
            case DUST -> DUST_REFRESH_TICKS;
        };
    }

    /** Whether the line is redrawn on this tick, for a refresh of {@code refreshTicks}. */
    static boolean refreshesOn(long gameTick, int refreshTicks) {
        return gameTick % refreshTicks == 0;
    }

    private static SendablePacket packetAt(Vec3 point, GuideLine style) {
        return new ParticlePacket(particleOf(style), OVERRIDE_LIMITER, LONG_DISTANCE,
                Vectors.toMinestom(point), NO_SPREAD, NO_SPEED, ONE_PARTICLE);
    }

    /**
     * The Minestom particle the map names. The only place in this package that maps the map's particle to
     * the server's, so a new particle is one case here and one entry in {@code GuideParticle}.
     */
    private static Particle particleOf(GuideLine style) {
        return switch (style.particle()) {
            case END_ROD -> Particle.END_ROD;
            case DUST -> Particle.DUST.withProperties(colourOf(style.dustRgbOrDefault()), DUST_SCALE);
        };
    }

    private static Color colourOf(int rgb) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
