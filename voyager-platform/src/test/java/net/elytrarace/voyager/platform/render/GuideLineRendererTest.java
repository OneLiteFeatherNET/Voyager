package net.elytrarace.voyager.platform.render;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.race.line.RacingLine;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which stretch of line a racer is shown, and what it costs to send.
 *
 * <p>Half of this class is decidable with no server at all — which points go out is arithmetic over
 * ring indices — and the other half needs a connection to answer, because "a particle" is a packet
 * addressed to one player. Both halves are here: the first against {@code stretch}, the second
 * against a real {@code Player} from Minestom's test environment, tracking what actually arrived.
 */
@EnvTest
class GuideLineRendererTest {

    /**
     * Five rings, 60 blocks apart and climbing, with a guide in the gap between rings 1 and 2 — so a
     * stretch that spans that gap has points that are not on any ring-to-ring chord, and no two gaps
     * hold the same number of points.
     */
    private static final List<Ring> RINGS = List.of(
            ring(0, new Vec3(0.0, 64.0, 0.0)),
            ring(1, new Vec3(0.0, 70.0, 60.0)),
            ring(2, new Vec3(40.0, 80.0, 110.0)),
            ring(3, new Vec3(40.0, 90.0, 170.0)),
            ring(4, new Vec3(0.0, 100.0, 220.0)));

    private static final GuidePoint AROUND_THE_CLIFF = new GuidePoint(150, new Vec3(20.0, 76.0, 80.0));

    /**
     * Two rings ahead, five blocks a particle. The look-ahead is not the committed map's and the
     * spacing is not either: both are read from the map, and a renderer that used a constant of its
     * own would draw the same line for every course.
     */
    private static final int LOOK_AHEAD = 2;

    private static final MapDefinition COURSE = new MapDefinition("ridge-run", "ridge_arena",
            new Vec3(0.5, 64.0, -10.0), RINGS, Duration.ofSeconds(30), new BoostConfig(12, 25),
            new GuideLine(List.of(AROUND_THE_CLIFF), LOOK_AHEAD, 5.0));

    private static final RacingLine LINE = RacingLine.of(COURSE);

    /**
     * The stretch starts at the ring the racer is heading for, never at the one behind them.
     *
     * <p>With two rings passed, the ring ahead is ring 2 and the ring behind is ring 1 — 50 blocks and
     * a guide point apart, so the two answers are not near each other. The line between a racer and a
     * ring they have already flown through is behind their head; drawing it spends packets on the one
     * part of the course that cannot help.
     */
    @Test
    void startsTheStretchAtTheRingAheadOfTheRacer() {
        List<Vec3> stretch = GuideLineRenderer.stretch(LINE, 2, LOOK_AHEAD);

        assertThat(stretch.getFirst()).isEqualTo(RINGS.get(2).center());
        assertThat(stretch).doesNotContain(RINGS.get(1).center(), AROUND_THE_CLIFF.position());
    }

    @Test
    void movesTheStretchForwardAsRingsArePassed() {
        assertThat(GuideLineRenderer.stretch(LINE, 0, LOOK_AHEAD).getFirst()).isEqualTo(RINGS.get(0).center());
        assertThat(GuideLineRenderer.stretch(LINE, 1, LOOK_AHEAD).getFirst()).isEqualTo(RINGS.get(1).center());
        assertThat(GuideLineRenderer.stretch(LINE, 2, LOOK_AHEAD).getFirst()).isEqualTo(RINGS.get(2).center());

        // And the far end moves with it: the racer who has passed one ring is shown as far as ring 3,
        // the one who has passed two as far as ring 4.
        assertThat(GuideLineRenderer.stretch(LINE, 1, LOOK_AHEAD).getLast()).isEqualTo(RINGS.get(3).center());
        assertThat(GuideLineRenderer.stretch(LINE, 2, LOOK_AHEAD).getLast()).isEqualTo(RINGS.get(4).center());
    }

    /**
     * Two rings ahead is two rings ahead, not the whole course.
     *
     * <p>This is the mutation that would otherwise pass every other assertion here: drawing the entire
     * line still starts at the right place for a racer at ring 0. It is the far end that says whether
     * the look-ahead was read, and at ring 0 the difference is ring 2 against ring 4.
     */
    @Test
    void reachesOnlyAsFarAheadAsTheMapAsksFor() {
        List<Vec3> stretch = GuideLineRenderer.stretch(LINE, 0, LOOK_AHEAD);

        assertThat(stretch.getLast()).isEqualTo(RINGS.get(2).center());
        assertThat(stretch).doesNotContain(RINGS.get(3).center(), RINGS.get(4).center());
        assertThat(stretch).hasSizeLessThan(LINE.points().size());

        // A different look-ahead is a different stretch, and one that reaches past the course is
        // clamped to it rather than failing.
        assertThat(GuideLineRenderer.stretch(LINE, 0, 1).getLast()).isEqualTo(RINGS.get(1).center());
        assertThat(GuideLineRenderer.stretch(LINE, 0, 99).getLast()).isEqualTo(RINGS.get(4).center());
    }

    @Test
    void showsNothingOnceThereIsNothingAhead() {
        // Heading for the last ring: the line ends there, so there is no stretch after it. One point
        // is what the geometry says and fewer than two is what render() declines to draw.
        assertThat(GuideLineRenderer.stretch(LINE, 4, LOOK_AHEAD)).containsExactly(RINGS.get(4).center());

        // Every ring passed: the racer has finished.
        assertThat(GuideLineRenderer.stretch(LINE, 5, LOOK_AHEAD)).isEmpty();
    }

    @Test
    void redrawsOnceEveryFourTicks() {
        assertThat(GuideLineRenderer.REFRESH_INTERVAL_TICKS).isEqualTo(4);
        assertThat(GuideLineRenderer.refreshesOn(0)).isTrue();
        assertThat(GuideLineRenderer.refreshesOn(1)).isFalse();
        assertThat(GuideLineRenderer.refreshesOn(2)).isFalse();
        assertThat(GuideLineRenderer.refreshesOn(3)).isFalse();
        assertThat(GuideLineRenderer.refreshesOn(4)).isTrue();
        assertThat(GuideLineRenderer.refreshesOn(8)).isTrue();
    }

    /**
     * One packet per point, at the points, with both visibility flags set.
     *
     * <p>A particle is a packet and {@code ParticlePacket} carries one position, so the cost of this
     * feature is the size of the stretch — which is the number this asserts, against the stretch the
     * line actually holds rather than against a constant. The flags are asserted because neither is
     * cosmetic: without them a racer sees only the nearest 32 blocks of a line that exists to show
     * them what is coming, and a client set to reduced particles sees none of it.
     */
    @Test
    void sendsOneParticlePacketPerPointOfTheStretch(Env env) {
        List<Vec3> expected = GuideLineRenderer.stretch(LINE, 1, LOOK_AHEAD);
        assertThat(expected).as("the fixture must produce a stretch worth counting").hasSizeGreaterThan(10);

        TestConnection connection = env.createConnection();
        Player racer = connection.connect(flatInstance(env), new Pos(0, 64, 0));
        Collector<ParticlePacket> particles = connection.trackIncoming(ParticlePacket.class);

        new GuideLineRenderer().render(racer, COURSE, 1, 0);

        List<ParticlePacket> sent = particles.collect();
        assertThat(sent).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(new Vec3(sent.get(i).x(), sent.get(i).y(), sent.get(i).z()))
                    .as("particle %s", i)
                    .isEqualTo(expected.get(i));
        }
        assertThat(sent).allMatch(ParticlePacket::longDistance);
        assertThat(sent).allMatch(ParticlePacket::overrideLimiter);
        assertThat(sent).allMatch(packet -> packet.particleCount() == 1);
    }

    @Test
    void sendsNothingOnATickBetweenRedrawsOrToARacerWithNothingAhead(Env env) {
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(flatInstance(env), new Pos(0, 64, 0));
        Collector<ParticlePacket> particles = connection.trackIncoming(ParticlePacket.class);
        GuideLineRenderer renderer = new GuideLineRenderer();

        renderer.render(racer, COURSE, 1, 1);
        renderer.render(racer, COURSE, 1, 2);
        renderer.render(racer, COURSE, 1, 3);
        // A refresh tick, but the racer has finished the course.
        renderer.render(racer, COURSE, 5, 4);
        // A refresh tick, and the racer is heading for the last ring: one point is not a line.
        renderer.render(racer, COURSE, 4, 8);

        assertThat(particles.collect()).isEmpty();
    }

    private static Instance flatInstance(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return instance;
    }

    private static Ring ring(int index, Vec3 center) {
        return new Ring(index, center, new Vec3(0.0, 0.0, 1.0), 5.0, 10, RingType.STANDARD);
    }
}
