package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.platform.world.ChunkLoader;
import net.elytrarace.voyager.platform.world.ChunkState;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The glide-out a racer takes after a finish or a time limit, and the hold that follows it, played through the cup on a
 * live test server and ticked by hand.
 *
 * <p>The placement on the next map is held by a chunk loader whose futures never complete, so a racer who has glided
 * out stays where the glide ended, and the test can watch the hold rather than the placement.
 */
@EnvTest
class GlideOutSessionTest {

    /** Ticks in a glide-out, as {@code GlideOut.TICKS} states them. */
    private static final int GLIDE_TICKS = 50;

    /** Lobby of one second, race of ten seconds, and a short end between maps. */
    private static final RaceTimings LONG_RACE = new RaceTimings(
            Duration.ofSeconds(1), Duration.ofSeconds(10), Duration.ofMillis(200), Duration.ofMillis(200));

    /** Race of one second, so the time limit ends the map while a racer is still in the air. */
    private static final RaceTimings SHORT_RACE = new RaceTimings(
            Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(200));

    @TempDir
    Path tempDir;

    /**
     * Set once the first map's placements are done: from then on every chunk stays loading, so the placement on the next
     * map never completes and a glided-out racer stays where the glide ended.
     */
    private final AtomicBoolean holdNextMap = new AtomicBoolean();

    /** The first map's placements load normally; after {@link #holdNextMap} is set, every chunk stays loading. */
    private ChunkLoader holdingNextMap() {
        return new ChunkLoader() {
            @Override
            public void start(Instance instance, int chunkX, int chunkZ) {
                if (!holdNextMap.get()) {
                    ChunkLoader.MINESTOM.start(instance, chunkX, chunkZ);
                }
            }

            @Override
            public ChunkState state(Instance instance, int chunkX, int chunkZ) {
                return holdNextMap.get()
                        ? ChunkState.LOADING
                        : ChunkLoader.MINESTOM.state(instance, chunkX, chunkZ);
            }
        };
    }

    /**
     * A finisher flies on for {@link #GLIDE_TICKS} ticks with a velocity on every one, then has the elytra off and no
     * velocity, and stays at the point where the glide ended.
     */
    @Test
    void aFinisherKeepsGlidingFor50TicksThenIsHeldInPlaceWithTheElytraOffUntilPlaced(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, LONG_RACE, holdingNextMap())) {
            Player finisher = cup.connectRacer();
            Player stillRacing = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(finisher, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    40, "the finisher's placement on the first map");
            cup.untilGameStarts();
            holdNextMap.set(true);
            finisher.setFlyingWithElytra(true);
            moveTo(finisher, PlacementFixture.FRONT_OF_FIRST_RING);
            cup.tick();
            moveTo(finisher, PlacementFixture.BEHIND_FIRST_RING);
            cup.tick();

            for (int tick = 1; tick <= GLIDE_TICKS; tick++) {
                cup.tick();
                assertThat(finisher.getVelocity())
                        .as("velocity sent on glide tick %s", tick)
                        .isNotEqualTo(Vec.ZERO);
            }
            // The server's own state is read straight after its phase step, before Minestom's physics runs on the tick.
            cup.session.advancePhase();
            assertThat(finisher.isFlyingWithElytra()).as("gliding after the glide-out").isFalse();
            assertThat(finisher.getVelocity()).as("velocity after the glide-out").isEqualTo(Vec.ZERO);
            Pos heldAt = finisher.getPosition();

            for (int tick = 0; tick < 5; tick++) {
                cup.session.advancePhase();
            }
            assertThat(finisher.getPosition().distance(heldAt))
                    .as("the finisher stays where the glide ended until placed")
                    .isLessThanOrEqualTo(0.1);
            assertThat(finisher.getInstance()).as("the finisher is not placed while the chunks are pending")
                    .isSameAs(cup.firstWorld);
            assertThat(stillRacing.getInstance()).isSameAs(cup.firstWorld);
        }
    }

    /**
     * A racer who is still in the air when the time limit ends gets the same glide-out, and a racer standing on the
     * ground gets none.
     */
    @Test
    void aRacerAirborneWhenTheTimeLimitEndsGetsTheSameGlideOutAndARacerOnTheGroundGetsNone(Env env)
            throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, SHORT_RACE, holdingNextMap())) {
            Player airborne = cup.connectRacer();
            Player onTheGround = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(onTheGround, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    40, "the first map's placement");
            cup.untilGameStarts();
            holdNextMap.set(true);
            airborne.setFlyingWithElytra(true);
            double x = airborne.getPosition().x();
            while (cup.session.describe().contains("phase GAME")) {
                x += 0.5;
                moveTo(airborne, new Vec3(x, PlacementFixture.FIRST_SPAWN.y(), PlacementFixture.FIRST_SPAWN.z()));
                cup.session.advancePhase();
            }

            assertThat(airborne.getVelocity())
                    .as("the airborne racer is given the glide-out when the limit ends")
                    .isNotEqualTo(Vec.ZERO);
            assertThat(onTheGround.getVelocity())
                    .as("the racer on the ground is given no glide-out")
                    .isEqualTo(Vec.ZERO);
        }
    }

    /** Moves a racer to {@code at}, the way a movement packet would, and waits for the move. */
    private static void moveTo(Player racer, Vec3 at) {
        racer.teleport(new Pos(at.x(), at.y(), at.z())).join();
    }
}
