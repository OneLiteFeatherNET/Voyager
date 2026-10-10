package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The directory reader the loader is built on. {@link CatalogLoaderTest} covers what a loaded
 * catalogue does with the result; these are the behaviours of the reading itself.
 */
class CatalogDirectoryTest {

    @Test
    void readsFilesInSortedOrderSoTheOrderIsTheSameOnEveryMachine(@TempDir Path maps) {
        // Written in one order, named in another. Directory iteration order is not stable across
        // filesystems, so without the sort the key order and the pair of filenames a duplicate error
        // names would differ between a laptop and the server.
        CatalogFixtures.map(maps, "zulu.json", "third", "W3");
        CatalogFixtures.map(maps, "alpha.json", "first", "W1");
        CatalogFixtures.map(maps, "mike.json", "second", "W2");

        Map<String, MapDefinition> read = read(maps, "map", MapDefinition.class, MapDefinition::name, new ArrayList<>());

        assertThat(read.keySet()).containsExactly("first", "second", "third");
    }

    @Test
    void keepsTheFirstFileOfTwoDeclaringTheSameNameAndRecordsTheSecondAsAProblem(@TempDir Path maps) {
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.map(maps, "zulu.json", "blue", "ADifferentWorld");
        List<CatalogProblem> problems = new ArrayList<>();

        Map<String, MapDefinition> read = read(maps, "map", MapDefinition.class, MapDefinition::name, problems);

        assertThat(read.get("blue").world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().source()).isEqualTo(maps.resolve("zulu.json"));
        assertThat(problems.getFirst().cause()).isInstanceOf(DuplicateCatalogEntryException.class);
        assertThat(problems.getFirst().message())
                .contains("'blue'", "alpha.json", "zulu.json");
    }

    @Test
    void ignoresFilesThatAreNotJson(@TempDir Path maps) {
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.write(maps, "alpha.json.bak", "{ not json at all");
        CatalogFixtures.write(maps, "notes.md", "# the maps");

        Map<String, MapDefinition> read = read(maps, "map", MapDefinition.class, MapDefinition::name, new ArrayList<>());

        assertThat(read.keySet()).containsExactly("blue");
    }

    @Test
    void doesNotDescendIntoSubdirectories(@TempDir Path maps) {
        // The old layout was a directory per map holding map.json and portals.json. A reader that
        // walked the tree would find those and try to parse them as the new format.
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.write(maps.resolve("old-layout"), "map.json", "{ \"uuid\": {} }");

        Map<String, MapDefinition> read = read(maps, "map", MapDefinition.class, MapDefinition::name, new ArrayList<>());

        assertThat(read.keySet()).containsExactly("blue");
    }

    @Test
    void ignoresADirectoryWhoseOwnNameEndsInJson(@TempDir Path maps) {
        // A directory called "archive.json" passes the suffix filter; without the regular-file check
        // it would be read and come back as "cannot be read" where the answer is "that is a folder".
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.write(maps.resolve("archive.json"), "old.json", "{}");

        Map<String, MapDefinition> read = read(maps, "map", MapDefinition.class, MapDefinition::name, new ArrayList<>());

        assertThat(read.keySet()).containsExactly("blue");
    }

    @Test
    void recordsAMissingDirectoryAsOneProblemAndReadsNothing(@TempDir Path root) {
        List<CatalogProblem> problems = new ArrayList<>();

        Map<String, CupDefinition> read = read(root.resolve("nothing"), "cup", CupDefinition.class, CupDefinition::name, problems);

        assertThat(read).isEmpty();
        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().cause()).isInstanceOf(UnreadableCatalogException.class);
        assertThat(problems.getFirst().message()).contains("cup catalogue directory");
    }

    private static <T> Map<String, T> read(Path directory, String kind, Class<T> type,
            Function<T, String> nameOf, List<CatalogProblem> problems) {
        return CatalogDirectory.readAll(directory, kind, type, nameOf, problems, new ArrayList<>());
    }
}
