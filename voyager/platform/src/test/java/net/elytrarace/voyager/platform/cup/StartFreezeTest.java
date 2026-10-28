package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.platform.flight.ArrivalFacing;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The freeze a placed racer stands in from placement until GO: at the spawn, facing the first ring, not gliding, and back
 * at the spawn if it moves. Played through the cup on a live test server, ticked by hand.
 */
@EnvTest
class StartFreezeTest {

    /** Lobby of two seconds, a one-second race that ends on its limit, and a short end between maps. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(200));

    /** How far a racer may drift from the spawn before the freeze returns them, in blocks. */
    private static final double TOLERANCE = 0.1;

    @TempDir
    Path tempDir;

    /**
     * A racer placed on the second map, at the end of the first map's race, stands at its spawn facing the second map's
     * first ring until GO, the same as a racer placed on the first map.
     */
    @Test
    void aPlacedRacerIsHeldAtTheSpawnFacingRingOneUntilGoOnTheSecondMapToo(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.secondWorld, PlacementFixture.SECOND_SPAWN),
                    2_000, "the racer's placement on the second map");
            cup.tickUntil(() -> cup.session.describe().contains("phase LOBBY")
                            && cup.session.describe().contains("map 2/2"),
                    2_000, "the second map's lobby");

            Pos facing = ArrivalFacing.at(PlacementFixture.SECOND_RUN);
            assertThat(racer.getPosition().yaw())
                    .as("yaw towards the second map's first ring")
                    .isCloseTo(facing.yaw(), within(1.0f));
            assertThat(racer.isFlyingWithElytra()).as("gliding while held").isFalse();
        }
    }

    /** A racer who walks more than a tenth of a block from the spawn before GO is returned to it, with no velocity. */
    @Test
    void aRacerWhoMovesMoreThanATenthOfABlockBeforeGoIsReturnedToTheSpawnWithZeroVelocity(Env env)
            throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    200, "the placement on the first map");
            // The placement is recorded on the tick after the racer stands on the spawn; the freeze holds from then on.
            cup.tick();

            racer.teleport(new Pos(PlacementFixture.FIRST_SPAWN.x() + 1.0, PlacementFixture.FIRST_SPAWN.y(),
                    PlacementFixture.FIRST_SPAWN.z())).join();
            cup.tick();

            assertThat(racer.getPosition().distance(spawnOf(PlacementFixture.FIRST_SPAWN)))
                    .as("distance from the spawn after the freeze returns the racer")
                    .isLessThanOrEqualTo(TOLERANCE);
            assertThat(racer.getVelocity().x()).as("horizontal velocity after the freeze returns the racer").isZero();
            assertThat(racer.getVelocity().z()).as("horizontal velocity after the freeze returns the racer").isZero();
        }
    }

    /**
     * A racer who jumps and deploys the elytra before GO is turned not-gliding on the tick the freeze sees it, so they are
     * not lifted off the spawn.
     */
    @Test
    void aRacerWhoDeploysTheElytraBeforeGoIsTurnedNotGlidingOnTheSameTick(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    200, "the placement on the first map");

            racer.setFlyingWithElytra(true);
            cup.tick();

            assertThat(racer.isFlyingWithElytra()).as("gliding after the freeze sees the deployment").isFalse();
        }
    }

    private static Pos spawnOf(Vec3 spawn) {
        return new Pos(spawn.x(), spawn.y(), spawn.z());
    }
}
