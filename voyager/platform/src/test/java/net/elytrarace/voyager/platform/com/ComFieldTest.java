package net.elytrarace.voyager.platform.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.platform.cup.Competitor;
import net.elytrarace.voyager.race.com.ComPath;
import net.elytrarace.voyager.race.com.ComState;
import net.elytrarace.voyager.race.com.ComTuning;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The computer racer field over a fake brain and explicit ticks. Each test gets a fresh {@link Env}, its own instance
 * and its own field; nothing sleeps and no test reads the wall clock.
 *
 * <p>The fake brain moves a racer to the tick plus its seat index and places it at {@code (seat, 65, distance)}, so
 * every position names its seat and its tick. The field is still the real one: it owns the bodies, the entries and the
 * path cache, and only the movement is faked.
 */
@EnvTest
class ComFieldTest {

    private static final long ROUND_SEED = 20261014L;
    private static final Duration STEP = Duration.ofMillis(50);
    private static final String WORLD = "ridge_arena";
    private static final Vec3 SPAWN = new Vec3(3.5, 65.0, 5.5);

    private static final MapDefinition MAP = new MapDefinition("ridge-run", WORLD, SPAWN,
            List.of(new Ring(0, SPAWN.plus(new Vec3(0, 8, 10)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD),
                    new Ring(1, SPAWN.plus(new Vec3(0, 8, 30)), new Vec3(0, 0, 1), 6.0, 7, RingType.STANDARD)),
            Duration.ofMillis(400), new BoostConfig(4, 9), new GuideLine(List.of(), 2, 1.0));

    @Test
    void prepareReturnsOneIdPerComputerRacer(Env env) {
        Instance instance = flatInstance(env);
        ComField field = field(instance, humans(env, instance, 2), 5, new FakeBrain());

        List<UUID> ids = field.prepare(0, MAP);

        assertThat(ids)
                .as("five on the grid with two humans leaves three computer racers")
                .hasSize(3)
                .doesNotHaveDuplicates();
        assertThat(ids)
                .as("every id returned is a computer racer's id")
                .allSatisfy(id -> assertThat(ComNames.isComputer(id)).isTrue());
    }

    @Test
    void advanceReturnsPositionsInSeatOrderAfterTheHumans(Env env) {
        Instance instance = flatInstance(env);
        ComField field = field(instance, List.of(), 3, new FakeBrain());
        List<UUID> ids = field.prepare(0, MAP);
        env.tick();

        List<Competitor> reported = field.advance(new RaceClock(3, STEP));

        assertThat(reported)
                .as("every computer racer is reported once, in the order the field prepared them")
                .extracting(Competitor::id)
                .containsExactlyElementsOf(ids);
        assertThat(reported)
                .as("each position names its seat and the tick it was reached on, so the seat order is visible")
                .extracting(Competitor::position)
                .containsExactly(new Vec3(0, 65, 3), new Vec3(1, 65, 4), new Vec3(2, 65, 5));
    }

    @Test
    void dismissRemovesEveryBodyAndEntry(Env env) {
        Instance instance = flatInstance(env);
        TestConnection connection = env.createConnection();
        Player human = connection.connect(instance, new Pos(0, 41, 0));
        ComField field = field(instance, List.of(human), 4, new FakeBrain());
        List<UUID> ids = field.prepare(0, MAP);
        env.tick();
        Collector<PlayerInfoRemovePacket> removed = connection.trackIncoming(PlayerInfoRemovePacket.class);
        assertThat(computerEntities(instance))
                .as("three bodies stand on the grid before the cup is dismissed")
                .hasSize(3);

        field.dismiss();
        env.tick();

        assertThat(computerEntities(instance))
                .as("no computer racer's body is left in the instance")
                .isEmpty();
        assertThat(removed.collect().stream().flatMap(packet -> packet.uuids().stream()).toList())
                .as("the human is told to remove every computer racer's entry")
                .containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void aCountOfZeroCreatesNothing(Env env) {
        Instance instance = flatInstance(env);
        ComField field = field(instance, List.of(), 0, new FakeBrain());

        List<UUID> ids = field.prepare(0, MAP);
        env.tick();

        assertThat(ids)
                .as("a count of zero asks for no computer racer")
                .isEmpty();
        assertThat(computerEntities(instance))
                .as("no body is spawned when the count is zero")
                .isEmpty();
    }

    @Test
    void aComputerRacerStartsOnTheGridAtTheMapsSpawn(Env env) {
        Instance instance = flatInstance(env);
        ComField field = new ComField(List::of, 1, ComDifficulty.MEDIUM, ROUND_SEED, world -> instance);

        field.prepare(0, MAP);
        env.tick();

        assertThat(computerEntities(instance))
                .as("one computer racer is on the grid")
                .hasSize(1);
        Pos start = computerEntities(instance).getFirst().getPosition();
        assertThat(start.y())
                .as("the racer starts at the spawn's height, not at the height of the first ring, which is eight blocks up")
                .isEqualTo(SPAWN.y());
        assertThat(Math.hypot(start.x() - SPAWN.x(), start.z() - SPAWN.z()))
                .as("the racer starts within a block of the spawn, where the wander of a MEDIUM racer keeps it")
                .isLessThanOrEqualTo(1.0);
    }

    @Test
    void thePathForAMapIsBuiltOnceAndShared(Env env) {
        Instance instance = flatInstance(env);
        FakeBrain brain = new FakeBrain();
        ComField field = field(instance, List.of(), 2, brain);

        field.prepare(0, MAP);
        field.advance(new RaceClock(1, STEP));
        field.prepare(0, MAP);
        field.advance(new RaceClock(2, STEP));

        assertThat(brain.pathsSeen)
                .as("both racers move on both maps' starts, so four steps are taken")
                .hasSize(4);
        ComPath shared = brain.pathsSeen.getFirst();
        assertThat(brain.pathsSeen)
                .as("every racer on the map steps along the same path object, built once")
                .allSatisfy(path -> assertThat(path).isSameAs(shared));
    }

    private static ComField field(Instance instance, List<Player> humans, int count, ComBrain brain) {
        return new ComField(() -> humans, count, ComDifficulty.MEDIUM, ROUND_SEED, world -> instance, brain);
    }

    private static List<Player> humans(Env env, Instance instance, int count) {
        List<Player> humans = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            humans.add(env.createConnection().connect(instance, new Pos(0, 41, 0)));
        }
        return humans;
    }

    private static List<Entity> computerEntities(Instance instance) {
        return instance.getEntities().stream()
                .filter(entity -> ComNames.isComputer(entity.getUuid()))
                .toList();
    }

    /** Loads the chunks the fake positions fall in: seats at x 0 to 2 and distances from -8 to 30. */
    private static Instance flatInstance(Env env) {
        Instance instance = env.createFlatInstance();
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                instance.loadChunk(cx, cz).join();
            }
        }
        return instance;
    }

    /** A brain that moves a racer to its seat index plus the tick, and places it at its distance on its column. */
    private static final class FakeBrain implements ComBrain {

        private final List<ComPath> pathsSeen = new ArrayList<>();

        @Override
        public ComState step(ComState state, ComTuning tuning, ComPath path, long tick) {
            pathsSeen.add(path);
            return new ComState(state.seat(), tick + state.seat().index(), state.speed(), state.progress(),
                    state.detour(), state.detourRing(), state.boostDueTick(), state.stalledTicks(), state.status());
        }

        @Override
        public Vec3 positionOf(ComState state, ComTuning tuning, ComPath path) {
            return new Vec3(state.seat().index(), 65.0, state.distance());
        }
    }
}
