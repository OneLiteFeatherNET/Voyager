package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.platform.world.ChunkLoader;
import net.elytrarace.voyager.platform.world.ChunkState;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A start countdown cancelled after its racers were already placed on the first map: the racers are sent back to the
 * lobby, stood down and handed back their waiting loadout, and a new countdown places them again as usual.
 *
 * <p>The lobby is ten seconds, so the cancel lands while the countdown is still open: a cancel is refused once the last
 * three seconds have begun.
 */
@EnvTest
class CountdownCancelTest {

    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(200));

    /** How far a racer may be from the lobby spot and still count as standing on it, in blocks. */
    private static final double TOLERANCE = 0.1;

    @TempDir
    Path tempDir;

    @Test
    void aRacerPlacedBeforeTheCancelIsSentBackToTheLobbyStoodDown(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player stayer = cup.connectRacer();
            Player leaver = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(stayer, cup.firstWorld, PlacementFixture.FIRST_SPAWN)
                            && PlacementFixture.atSpawn(leaver, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    200, "both racers placed on the first map");
            // The placement is recorded on the tick after a racer stands on the spawn.
            cup.tick();
            cup.online.remove(leaver);
            leaver.remove(true);

            cup.session.disarm(cup.lobby, lobbySpot());
            cup.tick();

            assertThat(stayer.getInstance())
                    .as("the world the remaining racer stands in after the cancel")
                    .isSameAs(cup.lobby);
            assertThat(stayer.getPosition().distance(lobbySpot()))
                    .as("distance from the lobby spot after the cancel")
                    .isLessThanOrEqualTo(TOLERANCE);
            assertThat(stayer.isFlyingWithElytra()).as("gliding after the cancel").isFalse();
            assertThat(stayer.getEquipment(EquipmentSlot.CHESTPLATE).material())
                    .as("chestplate after the cancel")
                    .isNotEqualTo(Material.ELYTRA);
        }
    }

    /** A racer sent back to the lobby is not held where the placement held them: they may walk about the lobby. */
    @Test
    void aRacerSentBackToTheLobbyIsFreeToMoveThere(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    200, "the racer placed on the first map");
            cup.tick();
            cup.session.disarm(cup.lobby, lobbySpot());
            cup.tick();

            racer.teleport(lobbySpot().add(2, 0, 0)).join();
            for (int tick = 0; tick < 5; tick++) {
                cup.tick();
            }

            assertThat(racer.getPosition().distance(lobbySpot().add(2, 0, 0)))
                    .as("distance from where the racer walked to, after ticks with no placement")
                    .isLessThanOrEqualTo(TOLERANCE);
        }
    }

    /** A racer whose move to the first map has begun when the countdown is cancelled is sent back to the lobby. */
    @Test
    void aRacerWhoseMoveHasBegunIsSentBackToTheLobby(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            // Begins the placement on this tick, and does not let the world change complete before the cancel.
            cup.session.advancePhase();

            cup.session.disarm(cup.lobby, lobbySpot());
            for (int tick = 0; tick < 40; tick++) {
                cup.tick();
            }

            assertThat(racer.getInstance())
                    .as("the world the racer stands in long after the cancel")
                    .isSameAs(cup.lobby);
        }
    }

    /** A placement still loading its chunks when the countdown is cancelled never moves its racer onto the map. */
    @Test
    void aPlacementStillLoadingWhenTheCancelComesDoesNotLandTheRacerOnTheMap(Env env) throws IOException {
        HeldChunks chunks = new HeldChunks();
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS, chunks)) {
            Player racer = cup.connectRacer();
            for (int tick = 0; tick < 3; tick++) {
                cup.tick();
            }
            assertThat(racer.getInstance())
                    .as("the racer before the cancel, while their chunks are still loading")
                    .isSameAs(cup.lobby);

            cup.session.disarm(cup.lobby, lobbySpot());
            chunks.release();
            for (int tick = 0; tick < 40; tick++) {
                cup.tick();
            }

            assertThat(racer.getInstance())
                    .as("the world the racer stands in once their chunks have loaded after the cancel")
                    .isSameAs(cup.lobby);
            assertThat(racer.getPosition().distance(lobbySpot()))
                    .as("distance from the lobby spot once their chunks have loaded after the cancel")
                    .isLessThanOrEqualTo(TOLERANCE);
        }
    }

    /** A new countdown after a cancel places the same racers on the first map again, as the first countdown did. */
    @Test
    void aNewCountdownPlacesTheRacersOnTheFirstMapAgain(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    200, "the racer placed on the first map");
            cup.tick();
            cup.session.disarm(cup.lobby, lobbySpot());
            cup.tick();

            cup.session.start(false);

            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    200, "the racer placed on the first map by the new countdown");
        }
    }

    private static Pos lobbySpot() {
        Vec3 spot = PlacementFixture.LOBBY_SPOT;
        return new Pos(spot.x(), spot.y(), spot.z());
    }

    /** A loader whose chunks stay loading until {@link #release()}, so a placement can be held in flight. */
    private static final class HeldChunks implements ChunkLoader {

        private boolean released;

        @Override
        public void start(Instance instance, int chunkX, int chunkZ) {
        }

        @Override
        public ChunkState state(Instance instance, int chunkX, int chunkZ) {
            return released ? ChunkState.PRESENT : ChunkState.LOADING;
        }

        void release() {
            released = true;
        }
    }
}
