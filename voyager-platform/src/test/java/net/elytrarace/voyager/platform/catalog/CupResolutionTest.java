package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.race.cup.exception.UnresolvedCupException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 *
 * <p>Each test writes its own {@code cups/} directory in its own {@code @TempDir}; no map directory is
 * written, because resolution does not look at maps.
 */
class CupResolutionTest {

    @TempDir
    Path root;

    @Test
    void resolvesTheCupNamedByTheProperty() {
        CatalogReading cups = catalogOf(
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
        CatalogReading cups = catalogOf(cup("zulu.json", "test_cup", "map-a"));

        assertThat(CupResolution.resolve(cups, Optional.empty()).name()).isEqualTo("test_cup");
    }

    @Test
    void refusesANameNoCupCarriesAndListsTheOnesThatAreThere() {
        CatalogReading cups = catalogOf(
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
        CatalogReading cups = catalogOf(
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
     * Owner decision, 2026-10-09: a broken second cup file makes an unnamed selection ambiguous, even
     * though only one cup parsed. The message names both files, so the operator can see which one was
     * not read and chooses with either switch.
     */
    @Test
    void unnamedSelectionIsAmbiguousWhenAMalformedSecondFileExists() {
        CatalogReading cups = catalogOf(
                cup("alpha.json", "alpha_series", "map-a"),
                broken("zulu.json"));

        assertThatThrownBy(() -> CupResolution.resolve(cups, Optional.empty()))
                .isInstanceOf(UnresolvedCupException.class)
                .hasMessageContaining("alpha.json")
                .hasMessageContaining("zulu.json")
                .hasMessageContaining("-Pcup=")
                .hasMessageContaining("-DVOYAGER_CUP=");
    }

    @Test
    void unnamedSelectionOfTheOnlyFileRefusesWithThatFilesProblem() {
        CatalogReading cups = catalogOf(broken("only.json"));

        assertThatThrownBy(() -> CupResolution.resolve(cups, Optional.empty()))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("only.json");
    }

    /**
     * A name that matches a file that does not parse names that file. Without this the operator would
     * be told "no cup named gamma", which is false: the file is there and it is the one that is wrong.
     */
    @Test
    void namedSelectionOfAMalformedFileNamesThatFile() {
        CatalogReading cups = catalogOf(
                cup("alpha.json", "alpha_series", "map-a"),
                broken("gamma.json"));

        assertThatThrownBy(() -> CupResolution.resolve(cups, Optional.of("gamma")))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("gamma.json");
    }

    @Test
    void unknownNamedSelectionKeepsTheNoSuchCupMessage() {
        CatalogReading cups = catalogOf(
                cup("alpha.json", "alpha_series", "map-a"),
                broken("zulu.json"));

        assertThatThrownBy(() -> CupResolution.resolve(cups, Optional.of("alhpa_series")))
                .isInstanceOf(UnresolvedCupException.class)
                .hasMessage("no cup named 'alhpa_series' in the catalogue; it holds [alpha_series]");
    }

    /**
     * A second file declaring the played cup's name makes the played cup ambiguous, so the server
     * refuses rather than choosing one of the two.
     */
    @Test
    void namedSelectionRefusesWhenAnotherFileDeclaresTheSameName() {
        CatalogReading cups = catalogOf(
                cup("alpha.json", "alpha_series", "map-a"),
                cup("zulu.json", "alpha_series", "map-b"));

        assertThatThrownBy(() -> CupResolution.resolve(cups, Optional.of("alpha_series")))
                .isInstanceOf(DuplicateCatalogEntryException.class)
                .hasMessageContaining("alpha.json")
                .hasMessageContaining("zulu.json");
    }

    /**
     * A broken file that is not the named cup does not stop the named cup from being played. It is
     * reported as skipped instead; see {@link #skippedCupsNameEveryProblemOfAnotherCup}.
     */
    @Test
    void namedSelectionIgnoresAMalformedFileThatIsNotTheNamedCup() {
        CatalogReading cups = catalogOf(
                cup("alpha.json", "alpha_series", "map-a"),
                broken("zulu.json"));

        assertThat(CupResolution.resolve(cups, Optional.of("alpha_series")).name()).isEqualTo("alpha_series");
    }

    /**
     * A cup of three maps resolves exactly as a cup of one does. Nothing here — and nothing
     * downstream of it — treats the rotation length as a special case, and this is where that is
     * pinned: the repository ships one cup listing one map, and one map must not become the shape the
     * code assumes.
     */
    @Test
    void resolvesACupOfAnyLengthWithItsRotationIntact() {
        CatalogReading cups = catalogOf(cup("zulu.json", "grand_tour", "ridge-run", "dune-run", "spire-run"));

        assertThat(CupResolution.resolve(cups, Optional.empty()).mapNames())
                .containsExactly("ridge-run", "dune-run", "spire-run");
    }

    /**
     * Every problem of an unplayed cup file becomes one line of the skipped list, in file order. A
     * cup that names a map nothing provides is listed too, so the boot warning is complete.
     */
    @Test
    void skippedCupsNameEveryProblemOfAnotherCup() {
        CatalogReading cups = catalogOf(
                cup("alpha.json", "alpha_series", "map-a"),
                cup("beta.json", "beta_series", "map-b"),
                broken("zulu.json"));
        CupDefinition played = CupResolution.resolve(cups, Optional.of("alpha_series"));

        List<String> skipped = CupResolution.skippedCups(cups, played);

        assertThat(skipped).hasSize(2);
        assertThat(skipped.getFirst()).contains("zulu.json");
        assertThat(skipped).anySatisfy(line -> assertThat(line).isEqualTo("cup 'beta_series' plays 'map-b'"));
    }

    @Test
    void theSkippedWarningCountsAndJoinsItsLines() {
        assertThat(CupResolution.skippedWarning(List.of("cup 'beta' plays 'map-b'", "gamma.json is broken")))
                .isEqualTo("2 cup(s) are not playable and were skipped: "
                        + "cup 'beta' plays 'map-b'; gamma.json is broken");
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    /** The reading of the cup files written, one per entry. Each entry is {@code filename\njson}. */
    private CatalogReading catalogOf(String... files) {
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
        return CatalogLoader.read(root);
    }

    /** Returns {@code filename\njson}, which {@link #catalogOf} splits back apart. */
    private static String cup(String filename, String name, String... mapNames) {
        String maps = java.util.Arrays.stream(mapNames)
                .map("\"%s\""::formatted)
                .collect(java.util.stream.Collectors.joining(", "));
        return "%s\n{\"name\": \"%s\", \"mode\": \"RACE\", \"mapNames\": [%s]}".formatted(filename, name, maps);
    }

    /** A cup file that will not parse. */
    private static String broken(String filename) {
        return filename + "\n";
    }
}
