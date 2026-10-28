package net.elytrarace.voyager.platform.cup;

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

/**
 * When a racer is placed on the next map, and what the previous map's race still does to a racer placed on it. Played
 * through the cup on a live test server, with the production chunk loader, so a placement completes once its chunks are
 * there.
 */
@EnvTest
class WaitArrivalTest {

    /** Lobby of two seconds, race of ten seconds, and a short end between maps. */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofMillis(200), Duration.ofMillis(200));

    /** Ticks a placement may take on a live server before the test calls it a failure. */
    private static final int PLACEMENT_BUDGET = 200;

    @TempDir
    Path tempDir;

    /**
     * The first racer to finish is placed on the second map at the end of their own glide-out, while the racer still on
     * the first map keeps racing it and is not moved.
     */
    @Test
    void anEarlyFinisherIsPlacedWhenTheirOwnGlideOutEndsAndTheRacersStillRacingAreNotMoved(Env env)
            throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player finisher = cup.connectRacer();
            Player stillRacing = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(stillRacing, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    60, "the first map's placement");
            cup.untilGameStarts();
            Pos racingAt = stillRacing.getPosition();
            finisher.setFlyingWithElytra(true);
            moveTo(finisher, PlacementFixture.FRONT_OF_FIRST_RING);
            cup.tick();
            moveTo(finisher, PlacementFixture.BEHIND_FIRST_RING);
            cup.tick();

            cup.tickUntil(() -> PlacementFixture.atSpawn(finisher, cup.secondWorld, PlacementFixture.SECOND_SPAWN),
                    PLACEMENT_BUDGET, "the finisher's placement on the second map");
            assertThat(cup.session.describe()).as("the first map is still racing").contains("phase GAME");
            assertThat(stillRacing.getInstance()).as("the racer still racing stays in the first map's world")
                    .isSameAs(cup.firstWorld);
            assertThat(stillRacing.getPosition().distance(racingAt))
                    .as("the racer still racing is not moved").isLessThanOrEqualTo(0.1);
        }
    }

    /**
     * The first map's wait starts at its first lobby tick: every racer is on the spawn, facing the first ring, before the
     * lobby has run out.
     */
    @Test
    void theFirstMapsWaitStartsAtItsFirstLobbyTickAndPlacesEveryRacerOnTheCup(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player first = cup.connectRacer();
            Player second = cup.connectRacer();

            cup.tickUntil(() -> PlacementFixture.atSpawn(first, cup.firstWorld, PlacementFixture.FIRST_SPAWN)
                            && PlacementFixture.atSpawn(second, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "every racer's placement on the first map");
            assertThat(cup.session.describe()).as("the lobby has not run out").contains("phase LOBBY");
        }
    }

    /**
     * A racer placed early keeps the run they flew on the previous map: it is not advanced, and it is scored at the end
     * of that map's phase with the finish it had.
     */
    @Test
    void aPlacedRacersRunOnThePreviousMapIsNotAdvancedAndIsStillScoredAtThePhaseEnd(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player finisher = cup.connectRacer();
            Player stillRacing = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(stillRacing, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    60, "the first map's placement");
            cup.untilGameStarts();
            finisher.setFlyingWithElytra(true);
            moveTo(finisher, PlacementFixture.FRONT_OF_FIRST_RING);
            cup.tick();
            moveTo(finisher, PlacementFixture.BEHIND_FIRST_RING);
            cup.tickUntil(() -> PlacementFixture.atSpawn(finisher, cup.secondWorld, PlacementFixture.SECOND_SPAWN),
                    PLACEMENT_BUDGET, "the finisher's placement on the second map");

            cup.tickUntil(() -> cup.session.describe().contains("phase END"), 2_000, "the first map's phase end");

            assertThat(cup.session.standings().of(finisher.getUuid()))
                    .as("the finisher's score on the first map").hasSize(1);
            assertThat(cup.session.standings().of(stillRacing.getUuid()))
                    .as("the racer still racing is scored at the phase end too").hasSize(1);
        }
    }

    /** A racer who joins during a wait is placed once their preload completes. */
    @Test
    void aRacerWhoJoinsDuringAWaitIsPlacedWhenTheirPreloadCompletes(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            cup.tick();
            Player late = cup.connectRacer();

            cup.tickUntil(() -> PlacementFixture.atSpawn(late, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the late racer's placement on the first map");
        }
    }

    /**
     * The GO of a map does not move a racer who was placed before it: their position at GO is the one the placement left.
     */
    @Test
    void goDoesNotMoveARacerWhoIsAlreadyPlaced(Env env) throws IOException {
        try (PlacementFixture cup = PlacementFixture.open(env, tempDir, TIMINGS)) {
            Player racer = cup.connectRacer();
            cup.tickUntil(() -> PlacementFixture.atSpawn(racer, cup.firstWorld, PlacementFixture.FIRST_SPAWN),
                    PLACEMENT_BUDGET, "the placement on the first map");
            Pos placedAt = racer.getPosition();

            cup.untilGameStarts();

            assertThat(racer.getPosition().distance(placedAt)).as("the racer's position at GO")
                    .isLessThanOrEqualTo(0.1);
            assertThat(racer.getInstance()).isSameAs(cup.firstWorld);
        }
    }

    /** Moves a racer to {@code at}, the way a movement packet would, and waits for the move. */
    private static void moveTo(Player racer, net.elytrarace.voyager.api.math.Vec3 at) {
        racer.teleport(new Pos(at.x(), at.y(), at.z())).join();
    }
}
