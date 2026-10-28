package net.elytrarace.voyager.platform.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideLineMode;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.adapter.MapDefinitionAdapter;
import net.elytrarace.voyager.platform.catalog.adapter.Vec3Adapter;
import net.elytrarace.voyager.race.line.RacingLine;
import net.minestom.server.color.Color;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which stretch of line a racer is shown, what it costs to send, and what a racer in a full race is sent.
 *
 * <p>Most of this class is decidable with no server at all: which points go out is arithmetic over ring
 * indices and the racer's position. The packets themselves need a connection to answer, because "a
 * particle" is a packet addressed to one player, so the tests that count what arrived use a real
 * {@code Player} from Minestom's test environment.
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

    /** Two rings ahead, five blocks a particle, for the window. */
    private static final int LOOK_AHEAD = 2;

    private static final GuideLine WINDOW_STYLE = new GuideLine(List.of(AROUND_THE_CLIFF), LOOK_AHEAD, 5.0,
            GuideLineMode.WINDOW, GuideParticle.END_ROD, null);

    private static final GuideLine FULL_STYLE = new GuideLine(List.of(AROUND_THE_CLIFF), LOOK_AHEAD, 5.0);

    private static final GuideLine DUST_STYLE = new GuideLine(List.of(AROUND_THE_CLIFF), LOOK_AHEAD, 5.0,
            GuideLineMode.FULL, GuideParticle.DUST, 0x3366FF);

    private static final MapDefinition WINDOW_COURSE = course(WINDOW_STYLE);
    private static final MapDefinition FULL_COURSE = course(FULL_STYLE);
    private static final MapDefinition DUST_COURSE = course(DUST_STYLE);

    private static final RacingLine LINE = RacingLine.of(WINDOW_COURSE);

    /** The stretch the renderer draws for a style, at a racer's passed count. */
    private static List<Vec3> stretchOf(RacingLine line, int passedCount, GuideLine style) {
        return GuideLineRenderer.stretch(line, passedCount, GuideLineRenderer.lookAheadOf(style));
    }

    /** A window of {@code lookAhead} rings, the rest of the style as the window course states it. */
    private static GuideLine window(int lookAhead) {
        return new GuideLine(List.of(AROUND_THE_CLIFF), lookAhead, 5.0, GuideLineMode.WINDOW, GuideParticle.END_ROD,
                null);
    }

    @Test
    void startsTheWindowAtTheRingAheadOfTheRacer() {
        List<Vec3> stretch = stretchOf(LINE, 2, WINDOW_STYLE);

        assertThat(stretch.getFirst()).isEqualTo(RINGS.get(2).center());
        assertThat(stretch).doesNotContain(RINGS.get(1).center(), AROUND_THE_CLIFF.position());
    }

    @Test
    void movesTheWindowForwardAsRingsArePassed() {
        assertThat(stretchOf(LINE, 0, WINDOW_STYLE).getFirst()).isEqualTo(RINGS.get(0).center());
        assertThat(stretchOf(LINE, 1, WINDOW_STYLE).getFirst()).isEqualTo(RINGS.get(1).center());
        assertThat(stretchOf(LINE, 2, WINDOW_STYLE).getFirst()).isEqualTo(RINGS.get(2).center());

        assertThat(stretchOf(LINE, 1, WINDOW_STYLE).getLast()).isEqualTo(RINGS.get(3).center());
        assertThat(stretchOf(LINE, 2, WINDOW_STYLE).getLast()).isEqualTo(RINGS.get(4).center());
    }

    /**
     * Two rings ahead is two rings ahead, not the whole course.
     *
     * <p>Drawing the entire line still starts at the right place for a racer at ring 0. It is the far end
     * that says whether the look-ahead was read.
     */
    @Test
    void reachesOnlyAsFarAheadAsTheMapAsksFor() {
        List<Vec3> stretch = stretchOf(LINE, 0, WINDOW_STYLE);

        assertThat(stretch.getLast()).isEqualTo(RINGS.get(2).center());
        assertThat(stretch).doesNotContain(RINGS.get(3).center(), RINGS.get(4).center());
        assertThat(stretch).hasSizeLessThan(LINE.points().size());

        // A different look-ahead is a different stretch, and one that reaches past the course is
        // clamped to it rather than failing.
        assertThat(stretchOf(LINE, 0, window(1)).getLast()).isEqualTo(RINGS.get(1).center());
        assertThat(stretchOf(LINE, 0, window(99)).getLast()).isEqualTo(RINGS.get(4).center());
    }

    @Test
    void showsNothingOnceThereIsNothingAheadInTheWindow() {
        // Heading for the last ring: one point is what the geometry says and fewer than two is what
        // render() declines to draw.
        assertThat(stretchOf(LINE, 4, WINDOW_STYLE)).containsExactly(RINGS.get(4).center());

        // Every ring passed: the racer has finished.
        assertThat(stretchOf(LINE, 5, WINDOW_STYLE)).isEmpty();
    }

    @Test
    void aFullLineSendsTheStretchFromTheNextRingToTheLast() {
        List<Vec3> stretch = stretchOf(LINE, 1, FULL_STYLE);

        assertThat(stretch.getFirst()).isEqualTo(RINGS.get(1).center());
        assertThat(stretch.getLast()).isEqualTo(RINGS.get(4).center());
    }

    @Test
    void aFullLineSendsNothingBehindTheNextRing() {
        List<Vec3> stretch = stretchOf(LINE, 2, FULL_STYLE);

        assertThat(stretch).doesNotContain(RINGS.get(0).center(), RINGS.get(1).center(), AROUND_THE_CLIFF.position());
    }

    @Test
    void aFullLineAtTheFirstRingIsTheWholeLine() {
        assertThat(stretchOf(LINE, 0, FULL_STYLE)).isEqualTo(LINE.points());
    }

    @Test
    void aDustLineRedrawsEveryFourTicks() {
        assertThat(GuideLineRenderer.refreshTicksOf(GuideParticle.DUST)).isEqualTo(4);
        assertThat(GuideLineRenderer.refreshesOn(0, 4)).isTrue();
        assertThat(GuideLineRenderer.refreshesOn(1, 4)).isFalse();
        assertThat(GuideLineRenderer.refreshesOn(3, 4)).isFalse();
        assertThat(GuideLineRenderer.refreshesOn(4, 4)).isTrue();
        assertThat(GuideLineRenderer.refreshesOn(8, 4)).isTrue();
    }

    /**
     * Six ticks, not the ten the owner proposed, because END_ROD's shortest life could not be read from a
     * source this change could reach. Six is at most half of any life of twelve ticks or more, so one
     * redraw always lands before the previous points go. design.md D4 records the choice.
     */
    @Test
    void anEndRodLineRedrawsEverySixTicks() {
        assertThat(GuideLineRenderer.refreshTicksOf(GuideParticle.END_ROD)).isEqualTo(6);
        assertThat(GuideLineRenderer.refreshesOn(0, 6)).isTrue();
        assertThat(GuideLineRenderer.refreshesOn(3, 6)).isFalse();
        assertThat(GuideLineRenderer.refreshesOn(6, 6)).isTrue();
        assertThat(GuideLineRenderer.refreshesOn(10, 6)).isFalse();
    }

    /**
     * One packet per point, at the points, with both visibility flags set.
     *
     * <p>A particle is a packet and {@code ParticlePacket} carries one position, so the cost of this
     * feature is the size of the stretch. The flags are asserted because neither is cosmetic: without
     * them a racer sees only the nearest 32 blocks of a line that exists to show them what is coming.
     * The racer stands on ring 1, so every point of the window is within the 160-block reach.
     */
    @Test
    void sendsOneParticlePacketPerPointOfTheStretchWithinReach(Env env) {
        Vec3 racerAt = RINGS.get(1).center();
        List<Vec3> expected = stretchOf(LINE, 1, WINDOW_STYLE).stream()
                .filter(point -> GuideLineRenderer.withinReach(point, racerAt))
                .toList();
        assertThat(expected).as("the fixture must produce a stretch worth counting").hasSizeGreaterThan(10);

        Collector<ParticlePacket> particles = trackParticlesAt(env, racerAt, WINDOW_COURSE, 1, 0);

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

    /** A point more than 160 blocks from the racer is not sent, and one within reach still is. */
    @Test
    void aPointMoreThan160BlocksAwayIsNotSent(Env env) {
        Vec3 racerAt = RINGS.get(0).center();
        assertThat(racerAt.distanceTo(RINGS.get(4).center())).isGreaterThan(GuideLineRenderer.REACH_BLOCKS);

        List<Vec3> sent = trackParticlesAt(env, racerAt, FULL_COURSE, 0, 0).collect().stream()
                .map(packet -> new Vec3(packet.x(), packet.y(), packet.z()))
                .toList();

        assertThat(sent).contains(RINGS.get(0).center());
        assertThat(sent).doesNotContain(RINGS.get(4).center());
    }

    @Test
    void aDustLineUsesTheMapsColour(Env env) {
        Collector<ParticlePacket> particles = trackParticlesAt(env, RINGS.get(0).center(), DUST_COURSE, 0, 0);

        List<ParticlePacket> sent = particles.collect();
        assertThat(sent).isNotEmpty();
        assertThat(sent).allMatch(packet -> packet.particle().equals(
                Particle.DUST.withProperties(new Color(0x33, 0x66, 0xFF), 1.0f)));
    }

    @Test
    void anEndRodLineUsesEndRod(Env env) {
        Collector<ParticlePacket> particles = trackParticlesAt(env, RINGS.get(0).center(), FULL_COURSE, 0, 0);

        List<ParticlePacket> sent = particles.collect();
        assertThat(sent).isNotEmpty();
        assertThat(sent).allMatch(packet -> packet.particle().equals(Particle.END_ROD));
    }

    @Test
    void sendsNothingBetweenRedrawsOrToARacerWithNothingAhead(Env env) {
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(flatInstance(env), new Pos(0, 64, 0));
        Collector<ParticlePacket> particles = connection.trackIncoming(ParticlePacket.class);
        GuideLineRenderer renderer = new GuideLineRenderer();

        // END_ROD redraws on ticks 0, 6, 12 and so on: none of these ticks is one.
        renderer.render(racer, WINDOW_COURSE, 1, 1);
        renderer.render(racer, WINDOW_COURSE, 1, 2);
        renderer.render(racer, WINDOW_COURSE, 1, 3);
        renderer.render(racer, WINDOW_COURSE, 1, 5);
        // A refresh tick, but the racer has finished the course.
        renderer.render(racer, WINDOW_COURSE, 5, 6);
        // A refresh tick, and the racer is heading for the last ring: one point is not a line.
        renderer.render(racer, WINDOW_COURSE, 4, 12);

        assertThat(particles.collect()).isEmpty();
    }

    /**
     * The shipped course with sixteen racers spread along it must stay inside the owner's budget: at most
     * 30,000 particle packets a second in total and 2,000 for any one racer. Measured over three seconds of
     * ticks and divided by three, with the racers' positions and the race clock explicit and no network.
     *
     * <p>The budget is the packets the server sends, so it is measured after the 160-block reach has taken
     * its share. The racers are placed at evenly spaced rings, one per two-ring step, which is the spread of
     * a full field and not a bunch at the start.
     */
    @Test
    void sixteenRacersOnTheShippedCourseStayWithinTheBudget(Env env) {
        MapDefinition shipped = readShippedCourse();
        RacingLine line = RacingLine.of(shipped);
        int racers = 16;
        int ringsPerRacerStep = (line.ringCount() - 1) / racers;

        Instance instance = env.createFlatInstance();
        loadChunksAround(instance, shipped);

        List<Collector<ParticlePacket>> perRacer = new ArrayList<>();
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            Vec3 at = shipped.rings().get(i * ringsPerRacerStep).center();
            TestConnection connection = env.createConnection();
            players.add(connection.connect(instance, new Pos(at.x(), at.y(), at.z())));
            perRacer.add(connection.trackIncoming(ParticlePacket.class));
        }

        // Three seconds of ticks: a multiple of every refresh interval (4, 6 and 10), so each count is a
        // whole number of redraws and the per-second figure is the steady rate, not a window's rounding.
        int seconds = 3;
        GuideLineRenderer renderer = new GuideLineRenderer();
        for (int tick = 0; tick < seconds * 20; tick++) {
            for (int i = 0; i < racers; i++) {
                renderer.render(players.get(i), shipped, i * ringsPerRacerStep, tick);
            }
        }

        long total = 0;
        for (int i = 0; i < racers; i++) {
            long sentToRacer = perRacer.get(i).collect().size() / seconds;
            assertThat(sentToRacer).as("packets per second to racer %s", i).isLessThanOrEqualTo(2_000);
            total += sentToRacer;
        }
        assertThat(total).as("packets per second in total").isLessThanOrEqualTo(30_000);
    }

    /** Places a racer on a course, runs one render, and returns the packets that reached it. */
    private static Collector<ParticlePacket> trackParticlesAt(Env env, Vec3 at, MapDefinition course,
                                                              int passedCount, long gameTick) {
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(flatInstance(env), new Pos(at.x(), at.y(), at.z()));
        Collector<ParticlePacket> particles = connection.trackIncoming(ParticlePacket.class);
        new GuideLineRenderer().render(racer, course, passedCount, gameTick);
        return particles;
    }

    private static Instance flatInstance(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return instance;
    }

    /** Loads every chunk the course's rings lie in, so that a racer placed on any ring stands on loaded ground. */
    private static void loadChunksAround(Instance instance, MapDefinition course) {
        int minX = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().x()).min().orElseThrow()) - 16;
        int maxX = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().x()).max().orElseThrow()) + 16;
        int minZ = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().z()).min().orElseThrow()) - 16;
        int maxZ = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().z()).max().orElseThrow()) + 16;
        for (int chunkX = Math.floorDiv(minX, 16); chunkX <= Math.floorDiv(maxX, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(minZ, 16); chunkZ <= Math.floorDiv(maxZ, 16); chunkZ++) {
                instance.loadChunk(chunkX, chunkZ).join();
            }
        }
    }

    /** The shipped course, read the way the game server reads it, from the file the server ships. */
    private static MapDefinition readShippedCourse() {
        try {
            Path file = Path.of("..", "server", "src", "main", "resources", "maps", "blue_and_red.json");
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(Vec3.class, new Vec3Adapter())
                    .registerTypeAdapter(MapDefinition.class, new MapDefinitionAdapter())
                    .create();
            return gson.fromJson(JsonParser.parseString(Files.readString(file)), MapDefinition.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("the shipped map must be readable from the platform tests", e);
        }
    }

    private static MapDefinition course(GuideLine style) {
        return new MapDefinition("ridge-run", "ridge_arena", new Vec3(0.5, 64.0, -10.0), RINGS,
                Duration.ofSeconds(30), new BoostConfig(12, 25), style);
    }

    private static Ring ring(int index, Vec3 center) {
        return new Ring(index, center, new Vec3(0.0, 0.0, 1.0), 5.0, 10, RingType.STANDARD);
    }
}
