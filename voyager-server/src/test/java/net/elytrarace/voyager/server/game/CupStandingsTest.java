package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.race.scoring.MapScore;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The board that accumulates a cup, and the two decisions in it: a map's score is keyed by that
 * map's index rather than appended, and the placement bonus is awarded once per map when the map
 * closes.
 *
 * <p>Every number in the fixtures is distinct from every other number that could stand in for it:
 * ring points, medal points, map indices and expected totals never coincide, so a board that read a
 * medal where a ring point belonged, or a map index where a rank belonged, cannot pass. The three
 * player ids are unrelated literals rather than values built from a loop counter, so a board keyed by
 * anything but the id it was handed would still line up if the ids were ordered.
 */
class CupStandingsTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BRUNO = UUID.fromString("99999999-8888-7777-6666-555555555550");
    private static final UUID CARLA = UUID.fromString("0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    @Test
    void keepsEachPlayersScorePerMap() {
        CupStandings standings = new CupStandings();

        standings.record(ALICE, 0, finished(70, MedalTier.GOLD, 12.500));
        standings.record(ALICE, 1, finished(110, MedalTier.SILVER, 19.250));
        standings.record(BRUNO, 0, dnf(30));

        assertThat(standings.of(ALICE)).hasSize(2);
        assertThat(standings.of(BRUNO)).hasSize(1);
        assertThat(standings.players()).containsExactlyInAnyOrder(ALICE, BRUNO);
    }

    /**
     * The decision keying by map index exists for. {@code /race start} restarts a cup that is already
     * running, and a board that appended would give the replayed map two rows and count its points
     * twice.
     */
    @Test
    void replacesAMapsScoreWhenThatMapIsPlayedAgainRatherThanCountingItTwice() {
        CupStandings standings = new CupStandings();

        standings.record(ALICE, 1, finished(110, MedalTier.SILVER, 19.250));
        standings.record(ALICE, 1, finished(350, MedalTier.DIAMOND, 8.125));

        assertThat(standings.of(ALICE)).hasSize(1);
        assertThat(standings.totalOf(ALICE).totalPoints()).isEqualTo(350 + MedalTier.DIAMOND.medalPoints());
        assertThat(standings.totalOf(ALICE).bestTime()).contains(millis(8.125));
    }

    @Test
    void returnsAMapsScoreByItsIndexAndNothingForAMapThePlayerHasNoResultOn() {
        CupStandings standings = new CupStandings();
        standings.record(ALICE, 2, finished(70, MedalTier.GOLD, 12.500));

        assertThat(standings.scoreOn(ALICE, 2)).isNotNull();
        assertThat(standings.scoreOn(ALICE, 0)).isNull();
        assertThat(standings.scoreOn(BRUNO, 2)).isNull();
    }

    // ------------------------------------------------------------------------------------------
    // Closing a map
    // ------------------------------------------------------------------------------------------

    /**
     * The bonus is by rank on {@code MapScore.total()}, not by the order the scores were recorded in.
     * The fixture records the lowest scorer first on purpose: a board that awarded 10 to whoever it
     * saw first would pass every other assertion here.
     */
    @Test
    void awardsThePlacementBonusByRankAndNotByTheOrderScoresWereRecordedIn() {
        CupStandings standings = new CupStandings();
        standings.record(CARLA, 0, dnf(30));                              // total 30
        standings.record(ALICE, 0, finished(70, MedalTier.GOLD, 12.500)); // total 70 + 45 = 115
        standings.record(BRUNO, 0, finished(70, MedalTier.BRONZE, 44.0)); // total 70 + 15 = 85

        standings.closeMap(0, GameMode.RACE);

        assertThat(requireScore(standings, ALICE, 0).placementBonus()).isEqualTo(10);
        assertThat(requireScore(standings, BRUNO, 0).placementBonus()).isEqualTo(6);
        assertThat(requireScore(standings, CARLA, 0).placementBonus()).isEqualTo(3);
    }

    /** {@code PRACTICE} does not chase placement, so nobody gets a bonus — not even the fastest. */
    @Test
    void awardsNoPlacementBonusInAPracticeCup() {
        CupStandings standings = new CupStandings();
        standings.record(ALICE, 0, finished(70, MedalTier.GOLD, 12.500));
        standings.record(BRUNO, 0, dnf(30));

        standings.closeMap(0, GameMode.PRACTICE);

        assertThat(requireScore(standings, ALICE, 0).placementBonus()).isZero();
        assertThat(requireScore(standings, BRUNO, 0).placementBonus()).isZero();
    }

    /**
     * Closing map 1 must not touch map 0's bonuses. A close that ranked the whole board rather than
     * one map's column would award twice on the first map and get the second map's ranking wrong.
     */
    @Test
    void closingOneMapLeavesAnotherMapsBonusesAlone() {
        CupStandings standings = new CupStandings();
        standings.record(ALICE, 0, finished(70, MedalTier.GOLD, 12.500));
        standings.record(BRUNO, 0, dnf(30));
        standings.closeMap(0, GameMode.RACE);

        standings.record(ALICE, 1, dnf(20));
        standings.record(BRUNO, 1, finished(110, MedalTier.DIAMOND, 9.0));
        standings.closeMap(1, GameMode.RACE);

        assertThat(requireScore(standings, ALICE, 0).placementBonus()).isEqualTo(10);
        assertThat(requireScore(standings, ALICE, 1).placementBonus()).isEqualTo(6);
        assertThat(requireScore(standings, BRUNO, 0).placementBonus()).isEqualTo(6);
        assertThat(requireScore(standings, BRUNO, 1).placementBonus()).isEqualTo(10);
    }

    @Test
    void closingAMapNobodyHasAScoreOnChangesNothing() {
        CupStandings standings = new CupStandings();
        standings.record(ALICE, 0, finished(70, MedalTier.GOLD, 12.500));

        standings.closeMap(4, GameMode.RACE);

        assertThat(requireScore(standings, ALICE, 0).placementBonus()).isZero();
    }

    // ------------------------------------------------------------------------------------------
    // The cup order
    // ------------------------------------------------------------------------------------------

    @Test
    void ordersTheCupByPointsDescending() {
        CupStandings standings = new CupStandings();
        standings.record(ALICE, 0, finished(70, MedalTier.BRONZE, 44.0));   // 85
        standings.record(BRUNO, 0, finished(110, MedalTier.DIAMOND, 9.0));  // 170
        standings.record(CARLA, 0, dnf(30));                                // 30

        assertThat(standings.cupOrder()).extracting(CupStanding::playerId)
                .containsExactly(BRUNO, ALICE, CARLA);
    }

    /**
     * A tie on points is broken by the faster racer. Without it the order is whatever the map's
     * iteration happens to be, which is not a rule anybody chose — the same defect
     * {@code PlacementBonus} exists to remove one layer down.
     */
    @Test
    void breaksATieOnPointsWithTheBetterTime() {
        CupStandings standings = new CupStandings();
        standings.record(ALICE, 0, finished(70, MedalTier.GOLD, 33.750));
        standings.record(BRUNO, 0, finished(70, MedalTier.GOLD, 12.500));

        assertThat(standings.cupOrder()).extracting(CupStanding::playerId).containsExactly(BRUNO, ALICE);
    }

    /** A racer who finished nothing has no time at all and sorts behind one who has, on equal points. */
    @Test
    void sortsARacerWithNoTimeBehindOneWithATimeOnEqualPoints() {
        CupStandings standings = new CupStandings();
        // 70 ring points + FINISH's 5 = 75; 75 ring points + DNF's 0 = 75.
        standings.record(ALICE, 0, finished(70, MedalTier.FINISH, 51.0));
        standings.record(BRUNO, 0, dnf(75));

        assertThat(standings.cupOrder()).extracting(CupStanding::playerId).containsExactly(ALICE, BRUNO);
        assertThat(standings.cupOrder()).extracting(standing -> standing.score().totalPoints())
                .containsExactly(75, 75);
    }

    @Test
    void clearingDropsEverythingSoARestartedCupIsNotAContinuationOfTheLastOne() {
        CupStandings standings = new CupStandings();
        standings.record(ALICE, 0, finished(70, MedalTier.GOLD, 12.500));

        standings.clear();

        assertThat(standings.players()).isEmpty();
        assertThat(standings.cupOrder()).isEmpty();
        assertThat(standings.of(ALICE)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    private static MapScore requireScore(CupStandings standings, UUID playerId, int mapIndex) {
        MapScore score = standings.scoreOn(playerId, mapIndex);
        assertThat(score).as("score for %s on map %s", playerId, mapIndex).isNotNull();
        return score;
    }

    private static MapScore finished(int ringPoints, MedalTier medal, double seconds) {
        return new MapScore(ringPoints, medal.medalPoints(), 0, Optional.of(millis(seconds)), medal);
    }

    private static MapScore dnf(int ringPoints) {
        return new MapScore(ringPoints, MedalTier.DNF.medalPoints(), 0, Optional.empty(), MedalTier.DNF);
    }

    private static Duration millis(double seconds) {
        return Duration.ofMillis(Math.round(seconds * 1000.0));
    }
}
