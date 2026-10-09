package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.server.game.exception.UnresolvedCupException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which cup the server plays.
 *
 * <p>The fixtures never let a filename stand in for a cup name — {@code autumn.json} declares
 * {@code winter_series} — because the catalogue is keyed by the name inside the file, and a
 * resolution that matched on the filename would otherwise pass. The three cups are also not in
 * alphabetical file order, so "the first one" is not the same answer as "the only one".
 */
class CupResolutionTest {

    @TempDir
    Path root;

    @Test
    void resolvesTheCupNamedByTheProperty() {
        CatalogSnapshot cups = catalogOf(
                cup("autumn.json", "winter_series", "map-a"),
                cup("bravo.json", "alpha_series", "map-b"));

        assertThat(CupResolution.resolve(cups, Optional.of("alpha_series")).name()).isEqualTo("alpha_series");
    }

    /**
     * The one-cup case, which is what the repository actually ships. Named separately from the
     * "resolve by name" case so a resolution that always took the first entry and ignored the name
     * cannot be green on both.
     */
    @Test
    void resolvesTheOnlyCupWhenNothingChoseOne() {
        CatalogSnapshot cups = catalogOf(cup("zulu.json", "test_cup", "map-a"));

        assertThat(CupResolution.resolve(cups, Optional.empty()).name()).isEqualTo("test_cup");
    }

    @Test
    void refusesANameNoCupCarriesAndListsTheOnesThatAreThere() {
        CatalogSnapshot cups = catalogOf(
                cup("autumn.json", "winter_series", "map-a"),
                cup("bravo.json", "alpha_series", "map-b"));

        assertThatThrownBy(() -> CupResolution.resolve(cups, Optional.of("summer_series")))
                .isInstanceOf(UnresolvedCupException.class)
                .hasMessageContaining("summer_series")
                .hasMessageContaining("winter_series")
                .hasMessageContaining("alpha_series");
    }

    /**
     * More than one cup and nothing chose: there is no cup that can be called "the" cup, so the
     * server refuses rather than picking whichever the directory iteration happened to hand over
     * first — which is not the same on two machines.
     */
    @Test
    void refusesToGuessWhenTheCatalogueHoldsMoreThanOneCupAndNothingChose() {
        CatalogSnapshot cups = catalogOf(
                cup("autumn.json", "winter_series", "map-a"),
                cup("bravo.json", "alpha_series", "map-b"));

        assertThatThrownBy(() -> CupResolution.resolve(cups, Optional.empty()))
                .isInstanceOf(UnresolvedCupException.class)
                .hasMessageContaining("VOYAGER_CUP")
                .hasMessageContaining("-Pcup=")
                .hasMessageContaining("winter_series")
                .hasMessageContaining("alpha_series");
    }

    /**
     * A cup of three maps resolves exactly as a cup of one does. Nothing here — and nothing
     * downstream of it — treats the rotation length as a special case, and this is where that is
     * pinned: the repository ships one cup listing one map, and one map must not become the shape the
     * code assumes.
     */
    @Test
    void resolvesACupOfAnyLengthWithItsRotationIntact() {
        CatalogSnapshot cups = catalogOf(cup("zulu.json", "grand_tour", "ridge-run", "dune-run", "spire-run"));

        assertThat(CupResolution.resolve(cups, Optional.empty()).mapNames())
                .containsExactly("ridge-run", "dune-run", "spire-run");
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    /**
     * The snapshot of the cups written. Read through the loader's {@code read}, so the map directory
     * is absent and the cross-catalogue check reports a problem; the cups are kept regardless, and the
     * resolution under test does not look at maps.
     */
    private CatalogSnapshot catalogOf(String... files) {
        Path cups = root.resolve("cups");
        try {
            Files.createDirectories(cups);
            for (String content : files) {
                int split = content.indexOf('\n');
                Files.writeString(cups.resolve(content.substring(0, split)), content.substring(split + 1));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return CatalogLoader.read(root).snapshot();
    }

    /** Returns {@code filename\njson}, which {@link #catalogOf} splits back apart. */
    private static String cup(String filename, String name, String... mapNames) {
        String maps = java.util.Arrays.stream(mapNames)
                .map("\"%s\""::formatted)
                .collect(java.util.stream.Collectors.joining(", "));
        return "%s\n{\"name\": \"%s\", \"mode\": \"RACE\", \"mapNames\": [%s]}".formatted(filename, name, maps);
    }
}
