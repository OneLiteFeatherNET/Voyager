package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.platform.world.WorldHealth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.elytrarace.voyager.platform.catalog.CatalogFixtures.cup;
import static net.elytrarace.voyager.platform.catalog.CatalogFixtures.map;
import static net.elytrarace.voyager.platform.catalog.CatalogFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The whole-catalogue check: every problem in one result, each with the absolute file and the field.
 *
 * <p>Every test writes its own data and worlds directories in its own {@code @TempDir} and supplies a
 * fake {@link CatalogValidation.HealthSource}, so no test reads a real world, starts Minestom or
 * depends on another test. The real source is exercised by the integration test in voyager-server.
 */
class CatalogValidationTest {

    @TempDir
    Path root;

    private Path data() {
        return root.resolve("data");
    }

    private Path worlds() {
        return root.resolve("worlds");
    }

    /** A health source that says every world is sound, and counts how often it was asked. */
    private static final class CountingHealth implements CatalogValidation.HealthSource {
        final List<String> asked = new ArrayList<>();

        @Override
        public WorldHealth healthOf(String world) {
            asked.add(world);
            return sound(world);
        }
    }

    private static WorldHealth sound(String world) {
        return new WorldHealth(world, 5, 0, 0, 0, Map.of(), 0);
    }

    /** A world directory with one region file, which is all the shallow check looks for. */
    private void worldWithRegionData(String world) throws IOException {
        Path region = worlds().resolve(world).resolve("region");
        Files.createDirectories(region);
        Files.writeString(region.resolve("r.0.0.mca"), "");
    }

    @Test
    void reportsAMalformedMapFileAndAMissingWorldInOneResult() throws IOException {
        write(data().resolve("maps"), "a-broken.json", "");
        map(data().resolve("maps"), "blue.json", "blue", "ElytraraceMissing");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue");
        Files.createDirectories(worlds());

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), new CountingHealth());

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0).source()).isEqualTo(data().resolve("maps/a-broken.json").toAbsolutePath().toString());
        assertThat(problems.get(0).severity()).isEqualTo(Severity.ERROR);
        assertThat(problems.get(1).source()).isEqualTo(data().resolve("maps/blue.json").toAbsolutePath().toString());
        assertThat(problems.get(1).key()).isEqualTo("world");
    }

    @Test
    void namesTheMapFileAndTheWorldFieldWhenTheWorldFolderIsMissing() throws IOException {
        map(data().resolve("maps"), "blue.json", "blue", "ElytraraceMissing");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue");
        Files.createDirectories(worlds());

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), new CountingHealth());

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.source()).isEqualTo(data().resolve("maps/blue.json").toAbsolutePath().toString());
            assertThat(problem.key()).isEqualTo("world");
            assertThat(problem.message()).contains(worlds().resolve("ElytraraceMissing").toAbsolutePath().toString());
        });
    }

    @Test
    void reportsAWorldFolderWithNoRegionDataAsAnErrorAndDoesNotAskForItsHealth() throws IOException {
        map(data().resolve("maps"), "blue.json", "blue", "EmptyWorld");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue");
        Files.createDirectories(worlds().resolve("EmptyWorld"));
        CountingHealth health = new CountingHealth();

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), health);

        assertThat(problems).singleElement().satisfies(problem -> assertThat(problem.message())
                .contains("holds no region data"));
        assertThat(health.asked).doesNotContain("EmptyWorld");
    }

    /**
     * A dangling cup entry is reported in the same result as a malformed file elsewhere. The check mode owes
     * the operator every problem at once, so one broken file must not hide the cross-catalogue check.
     */
    @Test
    void aDanglingCupEntryIsReportedAlongsideAMalformedOtherFile() throws IOException {
        write(data().resolve("maps"), "a-broken.json", "");
        map(data().resolve("maps"), "blue.json", "blue", "ElytraraceBlue");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue", "no-such-map");
        worldWithRegionData("ElytraraceBlue");

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), new CountingHealth());

        // Sorted by source, so cups/ comes before maps/.
        assertThat(problems).extracting(ConfigProblem::source, ConfigProblem::key).containsExactly(
                tuple(data().resolve("cups/c.json").toAbsolutePath().toString(), "no-such-map"),
                tuple(data().resolve("maps/a-broken.json").toAbsolutePath().toString(), "file"));
    }

    @Test
    void namesTheCupFileAndTheMapEntryWhenACupPlaysAnUnknownMap() throws IOException {
        map(data().resolve("maps"), "blue.json", "blue", "ElytraraceBlue");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue", "no-such-map");
        worldWithRegionData("ElytraraceBlue");

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), new CountingHealth());

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.source()).isEqualTo(data().resolve("cups/c.json").toAbsolutePath().toString());
            assertThat(problem.key()).isEqualTo("no-such-map");
            assertThat(problem.severity()).isEqualTo(Severity.ERROR);
        });
    }

    @Test
    void aValidConfigurationYieldsNoProblemAndTheSameResultOnEveryRun() throws IOException {
        map(data().resolve("maps"), "blue.json", "blue", "ElytraraceBlue");
        map(data().resolve("maps"), "red.json", "red", "ElytraraceRed");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue", "red");
        worldWithRegionData("ElytraraceBlue");
        worldWithRegionData("ElytraraceRed");

        List<ConfigProblem> first = CatalogValidation.validate(data(), worlds(), new CountingHealth());
        List<ConfigProblem> second = CatalogValidation.validate(data(), worlds(), new CountingHealth());

        assertThat(first).isEmpty();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void reportsAWorldWhoseChunksWereRefusedNamingTheVersions() throws IOException {
        map(data().resolve("maps"), "blue.json", "blue", "ElytraraceBlue");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue");
        worldWithRegionData("ElytraraceBlue");
        CatalogValidation.HealthSource refused = world -> new WorldHealth(
                world, 3, 0, 0, 2, Map.of("1400", 2L), 0);

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), refused);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("world");
            assertThat(problem.message()).contains("refused for their data version").contains("1400 x 2");
        });
    }

    @Test
    void reportsAWorldWithNoChunkReadAsAnErrorSayingSo() throws IOException {
        map(data().resolve("maps"), "blue.json", "blue", "ElytraraceBlue");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "blue");
        worldWithRegionData("ElytraraceBlue");
        CatalogValidation.HealthSource unread = world -> new WorldHealth(world, 0, 0, 0, 0, Map.of(), 0);

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), unread);

        assertThat(problems).singleElement().satisfies(problem ->
                assertThat(problem.message()).contains("no chunk was read at all"));
    }

    @Test
    void aFailingWorldDoesNotHideTheNextOne() throws IOException {
        map(data().resolve("maps"), "a.json", "a", "WorldA");
        map(data().resolve("maps"), "b.json", "b", "WorldB");
        cup(data().resolve("cups"), "c.json", "c", "RACE", "a", "b");
        worldWithRegionData("WorldA");
        worldWithRegionData("WorldB");
        CountingHealth second = new CountingHealth();
        CatalogValidation.HealthSource throwsForA = world -> {
            if (world.equals("WorldA")) {
                throw new IllegalStateException("Falco could not open r.0.0.mca");
            }
            return second.healthOf(world);
        };

        List<ConfigProblem> problems = CatalogValidation.validate(data(), worlds(), throwsForA);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.source()).isEqualTo(data().resolve("maps/a.json").toAbsolutePath().toString());
            assertThat(problem.message()).contains("Falco could not open r.0.0.mca");
        });
        assertThat(second.asked).containsExactly("WorldB");
    }
}
