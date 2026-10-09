package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;
import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupMapException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one entry point that reads a data directory's {@code maps/} and {@code cups/}.
 *
 * <p>Tests that are about what a definition reads back use {@link #snapshotOf}, which ignores the
 * problems list: a map read on its own is the subject, and a missing {@code cups/} or a dangling
 * cup reference is a different concern with its own tests further down. Tests about refusing boot
 * call {@link CatalogLoader#load} and assert the exception it throws.
 */
class CatalogLoaderTest {

    // ---------------------------------------------------------------------------------------------
    // Reading definitions back
    // ---------------------------------------------------------------------------------------------

    @Test
    void readsAMapUnderTheNameInsideTheFileRatherThanTheFilename(@TempDir Path data) {
        // The filename and the declared name disagree on purpose: keying by filename would answer
        // mapByName("elytraraceblueandred") with nothing, which is the failure this loader exists to
        // rule out.
        CatalogFixtures.map(data.resolve("maps"), "01-first.json", "elytraraceblueandred", "ElytraraceBlueAndRed");

        CatalogSnapshot snapshot = snapshotOf(data);

        assertThat(snapshot.mapByName("elytraraceblueandred")).isPresent();
        assertThat(snapshot.mapByName("01-first")).isEmpty();
    }

    @Test
    void readsEveryFieldOfADefinitionBack(@TempDir Path data) {
        // Every value in the fixture differs from every other, so a field read into the wrong slot
        // cannot pass: the spawn's y is -62 where the first ring's centre is at -54, and the two
        // rings differ in centre, normal, radius, score and type.
        CatalogFixtures.map(data.resolve("maps"), "map.json", "blue", "ElytraraceBlueAndRed");

        MapDefinition map = snapshotOf(data).mapByName("blue").orElseThrow();

        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(map.spawn()).isEqualTo(new Vec3(109, -62, 54));
        assertThat(map.referenceTime()).isEqualTo(Duration.ofMillis(46_700));
        assertThat(map.boostConfig()).isEqualTo(new BoostConfig(18, 41));
        assertThat(map.rings()).extracting(Ring::index).containsExactly(0, 1);
        assertThat(map.rings()).extracting(Ring::type).containsExactly(RingType.STANDARD, RingType.BOOST);
        assertThat(map.rings()).extracting(Ring::points).containsExactly(10, 25);
        assertThat(map.rings().get(0).center()).isEqualTo(new Vec3(85, -54, 54));
        assertThat(map.rings().get(0).normal()).isEqualTo(new Vec3(-1, 0, 0));
        assertThat(map.rings().get(0).radius()).isEqualTo(Math.sqrt(13));
        assertThat(map.rings().get(1).center()).isEqualTo(new Vec3(2, -31, 69));
        assertThat(map.rings().get(1).radius()).isEqualTo(3.0);
    }

    @Test
    void keepsEveryMapInTheDirectoryApart(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "a.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.map(data.resolve("maps"), "b.json", "sprint", "NetherSprint");

        CatalogSnapshot snapshot = snapshotOf(data);

        assertThat(snapshot.mapNames()).containsExactlyInAnyOrder("blue", "sprint");
        assertThat(snapshot.mapByName("blue").orElseThrow().world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(snapshot.mapByName("sprint").orElseThrow().world()).isEqualTo("NetherSprint");
    }

    @Test
    void answersEmptyForAMapItDoesNotKnow(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "a.json", "blue", "ElytraraceBlueAndRed");

        assertThat(snapshotOf(data).mapByName("frozen-cathedral")).isEmpty();
    }

    @Test
    void readsACupUnderTheNameInsideTheFileRatherThanTheFilename(@TempDir Path data) {
        CatalogFixtures.cup(data.resolve("cups"), "01-cup.json", "test_cup", "RACE", "blue");

        CatalogSnapshot snapshot = snapshotOf(data);

        assertThat(snapshot.cupByName("test_cup")).isPresent();
        assertThat(snapshot.cupByName("01-cup")).isEmpty();
    }

    @Test
    void keepsTheRotationInFileOrder(@TempDir Path data) {
        // Names chosen so that alphabetical order and file order disagree: a reader that sorted the
        // rotation would put "blue" first, and the cup would play its maps in the wrong sequence.
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "PRACTICE", "sprint", "blue", "cathedral");

        CupDefinition cup = snapshotOf(data).cupByName("test_cup").orElseThrow();

        assertThat(cup.mapNames()).containsExactly("sprint", "blue", "cathedral");
        assertThat(cup.mode()).isEqualTo(GameMode.PRACTICE);
    }

    @Test
    void readsEveryModeTheEnumOffers(@TempDir Path data) {
        CatalogFixtures.cup(data.resolve("cups"), "race.json", "ranked", "RACE", "blue");
        CatalogFixtures.cup(data.resolve("cups"), "practice.json", "casual", "PRACTICE", "blue");

        CatalogSnapshot snapshot = snapshotOf(data);

        assertThat(snapshot.cupNames()).containsExactlyInAnyOrder("ranked", "casual");
        assertThat(snapshot.cupByName("ranked").orElseThrow().mode()).isEqualTo(GameMode.RACE);
        assertThat(snapshot.cupByName("casual").orElseThrow().mode()).isEqualTo(GameMode.PRACTICE);
    }

    @Test
    void answersEmptyForACupItDoesNotKnow(@TempDir Path data) {
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE", "blue");

        assertThat(snapshotOf(data).cupByName("weekly")).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Refusals a definition file can cause
    // ---------------------------------------------------------------------------------------------

    @Test
    void refusesAMapFileThatIsNotJson(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "broken.json", "{ \"name\": ");
        writeValidCup(data);

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("broken.json");
    }

    @Test
    void refusesAMapWhoseDeclaredRingIndexIsNotItsPosition(@TempDir Path data) {
        // Well-formed JSON with a ring whose declared index contradicts its position. The exception has
        // to name the file all the same, and the position and the declared index.
        CatalogFixtures.write(data.resolve("maps"), "out-of-order.json", CatalogFixtures.MAP
                .formatted("blue", "ElytraraceBlueAndRed").replace("\"index\": 1", "\"index\": 7"));
        writeValidCup(data);

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("out-of-order.json")
                .hasMessageContaining("ring at position 1 declares index 7");
    }

    @Test
    void refusesAMapWhoseNormalIsNotUnitLength(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "stretched.json", CatalogFixtures.MAP
                .formatted("blue", "ElytraraceBlueAndRed")
                .replace("\"x\": -1.0, \"y\": 0.0, \"z\": 0.0", "\"x\": -1.4, \"y\": 0.0, \"z\": 0.0"));
        writeValidCup(data);

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("stretched.json");
    }

    @Test
    void refusesACupWithAnUnrecognisedMode(@TempDir Path data) {
        // Refused, not defaulted to RACE: a cup silently promoted into the ranked mode writes a
        // practice session into the records.
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "TOURNAMENT", "blue");
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("unrecognised mode 'TOURNAMENT'");
    }

    @Test
    void refusesACupThatPlaysNoMaps(@TempDir Path data) {
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE");
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("cup.json");
    }

    @Test
    void refusesAMapNameTwoFilesDeclare(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.map(data.resolve("maps"), "zulu.json", "blue", "ADifferentWorld");
        writeValidCup(data);

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(DuplicateCatalogEntryException.class)
                .hasMessageContaining("'blue'")
                .hasMessageContaining("alpha.json")
                .hasMessageContaining("zulu.json");
    }

    // ---------------------------------------------------------------------------------------------
    // Directories
    // ---------------------------------------------------------------------------------------------

    @Test
    void namesAMapDirectoryThatIsNotThere(@TempDir Path data) {
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE", "blue");

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(UnreadableCatalogException.class)
                .hasMessage("the map catalogue directory %s does not exist".formatted(data.resolve("maps")));
    }

    @Test
    void refusesAMapDirectoryWithNoDefinitionsInIt(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "readme.txt", "the maps used to live here");
        writeValidCup(data);

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(UnreadableCatalogException.class)
                .hasMessageContaining("holds no .json file");
    }

    @Test
    void refusesACupDirectoryWithNoDefinitionsInIt(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("cups"), "cups.yaml", "- name: test_cup");
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(UnreadableCatalogException.class)
                .hasMessageContaining("holds no .json file");
    }

    // ---------------------------------------------------------------------------------------------
    // Problems as data
    // ---------------------------------------------------------------------------------------------

    @Test
    void readReturnsEveryMalformedMapFileAsAProblem(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "b-broken.json", "");
        CatalogFixtures.write(data.resolve("maps"), "a-broken.json", "");
        writeValidCup(data);

        CatalogReading reading = CatalogLoader.read(data);

        assertThat(reading.problems()).hasSize(2);
        assertThat(reading.problems()).extracting(CatalogProblem::source)
                .containsExactly(data.resolve("maps/a-broken.json"), data.resolve("maps/b-broken.json"));
        assertThat(reading.problems()).allSatisfy(problem ->
                assertThat(problem.cause()).isInstanceOf(MalformedCatalogFileException.class));
    }

    @Test
    void missingCupsDirectoryIsOneProblemAndMapsAreStillRead(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");

        CatalogReading reading = CatalogLoader.read(data);

        assertThat(reading.problems()).hasSize(1);
        assertThat(reading.problems().getFirst().source()).isEqualTo(data.resolve("cups"));
        assertThat(reading.problems().getFirst().message())
                .isEqualTo("the cup catalogue directory %s does not exist".formatted(data.resolve("cups")));
        assertThat(reading.snapshot().mapNames()).containsExactly("blue");
    }

    @Test
    void duplicateMapNameIsAProblemNamingBothFiles(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.map(data.resolve("maps"), "zulu.json", "blue", "ADifferentWorld");
        writeValidCup(data);

        CatalogReading reading = CatalogLoader.read(data);

        assertThat(reading.problems()).hasSize(1);
        assertThat(reading.problems().getFirst().cause()).isInstanceOf(DuplicateCatalogEntryException.class);
        assertThat(reading.problems().getFirst().message())
                .contains("'blue'", data.resolve("maps/alpha.json").toString(), data.resolve("maps/zulu.json").toString());
    }

    @Test
    void readingTheSameDirectoryTwiceGivesTheSameProblemsInTheSameOrder(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "b-broken.json", "");
        CatalogFixtures.write(data.resolve("maps"), "a-broken.json", "");
        CatalogFixtures.map(data.resolve("maps"), "c.json", "blue", "ElytraraceBlueAndRed");
        writeValidCup(data);

        // Compared by source and message: two exception objects are never equal, but the same file
        // must always yield the same problem with the same text.
        CatalogReading first = CatalogLoader.read(data);
        CatalogReading second = CatalogLoader.read(data);

        assertThat(sourcesAndMessages(second)).isEqualTo(sourcesAndMessages(first));
        assertThat(second.snapshot().mapNames()).isEqualTo(first.snapshot().mapNames());
    }

    @Test
    void readListsTheCupProblemBeforeTheMapProblem(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "a-broken.json", "");
        CatalogFixtures.write(data.resolve("cups"), "a-broken.json", "");

        CatalogReading reading = CatalogLoader.read(data);

        assertThat(reading.problems()).extracting(CatalogProblem::source)
                .containsExactly(data.resolve("cups/a-broken.json"), data.resolve("maps/a-broken.json"));
    }

    @Test
    void readOfACleanDirectoryHasNoProblems(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE", "blue");

        assertThat(CatalogLoader.read(data).problems()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Boot policy: load refuses on the first problem
    // ---------------------------------------------------------------------------------------------

    @Test
    void loadReturnsTheSnapshotOfACleanDirectory(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE", "blue");

        CatalogSnapshot snapshot = CatalogLoader.load(data);

        assertThat(snapshot.mapByName("blue")).isPresent();
        assertThat(snapshot.cupByName("test_cup")).isPresent();
    }

    @Test
    void loadRefusesWithTheFirstMalformedFileExceptionAndItsMessage(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "b-broken.json", "");
        CatalogFixtures.write(data.resolve("maps"), "a-broken.json", "");
        writeValidCup(data);

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessage("%s is not a valid definition: the file is empty".formatted(data.resolve("maps/a-broken.json")));
    }

    @Test
    void loadRefusesWithTheCupFileWhenAMapAndACupAreBothMalformed(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "a-broken.json", "");
        CatalogFixtures.write(data.resolve("cups"), "a-broken.json", "");

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessage("%s is not a valid definition: the file is empty".formatted(data.resolve("cups/a-broken.json")));
    }

    @Test
    void loadRefusesWithTodaysUnresolvedCupMapException(@TempDir Path data) {
        CatalogFixtures.map(data.resolve("maps"), "blue.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE", "cathedral");

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(UnresolvedCupMapException.class)
                .hasMessage("1 cup entry does name a map no map definition provides: cup 'test_cup' plays 'cathedral'");
    }

    @Test
    void crossCatalogueCheckIsSkippedWhenAMapFileIsMalformed(@TempDir Path data) {
        CatalogFixtures.write(data.resolve("maps"), "a-broken.json", "");
        CatalogFixtures.cup(data.resolve("cups"), "cup.json", "test_cup", "RACE", "cathedral");

        assertThatThrownBy(() -> CatalogLoader.load(data))
                .isInstanceOf(MalformedCatalogFileException.class)
                .satisfies(refusal -> assertThat(refusal.getMessage()).doesNotContain("plays"));
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private static CatalogSnapshot snapshotOf(Path data) {
        return CatalogLoader.read(data).snapshot();
    }

    /**
     * A valid cup, so {@code cups/} is not the problem in a test about {@code maps/}. It names a map
     * the test does not write; that dangling reference is never checked while a directory has a
     * problem, which is itself one of the behaviours under test.
     */
    private static void writeValidCup(Path data) {
        CatalogFixtures.cup(data.resolve("cups"), "valid-cup.json", "valid_cup", "RACE", "blue");
    }

    private static List<String> sourcesAndMessages(CatalogReading reading) {
        return reading.problems().stream()
                .map(problem -> "%s: %s".formatted(problem.source(), problem.message()))
                .toList();
    }
}
