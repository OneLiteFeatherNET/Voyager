package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The directory reader both catalogues share. {@link JsonMapCatalogTest} and
 * {@link JsonCupCatalogTest} cover what each catalogue does with the result; these are the
 * behaviours that belong to the reading itself, and that neither of those two would notice if it
 * changed.
 */
class CatalogDirectoryTest {

    @Test
    void readsFilesInSortedOrderSoTheOrderIsTheSameOnEveryMachine(@TempDir Path maps) {
        // Written in one order, named in another. Directory iteration order is not stable across
        // filesystems, so without the sort the resulting key order — and the pair of filenames a
        // duplicate error names — would differ between a laptop and the server.
        CatalogFixtures.map(maps, "zulu.json", "third", "W3");
        CatalogFixtures.map(maps, "alpha.json", "first", "W1");
        CatalogFixtures.map(maps, "mike.json", "second", "W2");

        Map<String, MapDefinition> read =
                CatalogDirectory.readAll(maps, "map", MapDefinition.class, MapDefinition::name);

        assertThat(read.keySet()).containsExactly("first", "second", "third");
    }

    @Test
    void refusesTwoFilesDeclaringTheSameNameAndNamesBoth(@TempDir Path maps) {
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.map(maps, "zulu.json", "blue", "ADifferentWorld");

        assertThatThrownBy(() -> CatalogDirectory.readAll(maps, "map", MapDefinition.class, MapDefinition::name))
                .isInstanceOf(DuplicateCatalogEntryException.class)
                .hasMessageContaining("'blue'")
                .hasMessageContaining("alpha.json")
                .hasMessageContaining("zulu.json");
    }

    @Test
    void ignoresFilesThatAreNotJson(@TempDir Path maps) {
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.write(maps, "alpha.json.bak", "{ not json at all");
        CatalogFixtures.write(maps, "notes.md", "# the maps");

        Map<String, MapDefinition> read =
                CatalogDirectory.readAll(maps, "map", MapDefinition.class, MapDefinition::name);

        assertThat(read.keySet()).containsExactly("blue");
    }

    @Test
    void doesNotDescendIntoSubdirectories(@TempDir Path maps) {
        // The old layout was a directory per map holding map.json and portals.json. A reader that
        // walked the tree would find those and try to parse them as the new format, so the boundary
        // is worth pinning: one flat directory of definitions.
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.write(maps.resolve("old-layout"), "map.json", "{ \"uuid\": {} }");

        Map<String, MapDefinition> read =
                CatalogDirectory.readAll(maps, "map", MapDefinition.class, MapDefinition::name);

        assertThat(read.keySet()).containsExactly("blue");
    }

    @Test
    void ignoresADirectoryWhoseOwnNameEndsInJson(@TempDir Path maps) {
        // A directory called "archive.json" passes the suffix filter, and without the regular-file
        // check it would be handed to readString and come back as "cannot be read" — an I/O error
        // where the answer is "that is a folder". Named like a definition on purpose: a
        // subdirectory with an ordinary name is already filtered by the suffix and proves nothing.
        CatalogFixtures.map(maps, "alpha.json", "blue", "ElytraraceBlueAndRed");
        CatalogFixtures.write(maps.resolve("archive.json"), "old.json", "{}");

        Map<String, MapDefinition> read =
                CatalogDirectory.readAll(maps, "map", MapDefinition.class, MapDefinition::name);

        assertThat(read.keySet()).containsExactly("blue");
    }

    @Test
    void namesTheKindItWasReadingInTheMessage(@TempDir Path root) {
        // The same reader serves both catalogues, so an error that said only "catalogue directory"
        // would leave an operator guessing which of the two paths was wrong.
        assertThatThrownBy(() -> CatalogDirectory.readAll(
                root.resolve("nothing"), "cup", CupDefinition.class, CupDefinition::name))
                .hasMessageContaining("cup catalogue directory");
    }
}
