package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.platform.catalog.exception.UnresolvedCupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogReloaderTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    // ---- the initial load ------------------------------------------------------------------------

    @Test
    void theInitialLoadResolvesTheChosenCupAndStampsTheLoadTime() {
        Path data = validCatalogue();
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        LoadedCatalog loaded = reloader.loadInitial(data, Optional.of("tour"));

        assertThat(loaded.cup().name()).isEqualTo("tour");
        assertThat(loaded.loadedAt()).isEqualTo(NOW);
        assertThat(loaded.rotation()).extracting(map -> map.name()).containsExactly("ridge", "dune");
    }

    @Test
    void theInitialLoadNamesAChosenCupThatTheDirectoryDoesNotHold() {
        Path data = validCatalogue();
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        assertThatThrownBy(() -> reloader.loadInitial(data, Optional.of("summer")))
                .isInstanceOf(UnresolvedCupException.class)
                .hasMessageContaining("summer");
    }

    @Test
    void theInitialLoadRefusesAMalformedMapFileByName() {
        Path data = validCatalogue();
        CatalogFixtures.write(data.resolve("maps"), "broken.json", "{ not json");
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        assertThatThrownBy(() -> reloader.loadInitial(data, Optional.of("tour")))
                .hasMessageContaining("broken.json");
    }

    @Test
    void theInitialLoadIgnoresAMalformedCupThatIsNotPlayed() {
        Path data = validCatalogue();
        CatalogFixtures.write(data.resolve("cups"), "spare.json", "{ not json");
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        assertThat(reloader.loadInitial(data, Optional.of("tour")).cup().name()).isEqualTo("tour");
    }

    // ---- reload: the outcome --------------------------------------------------------------------

    @Test
    void aValidReloadIsAppliedWithTheNewLoadTimeAndNoWarning() {
        Path data = validCatalogue();
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener("ridge-world", "dune-world"));

        ReloadOutcome outcome = reloader.reload(data, Optional.of("tour"));

        assertThat(outcome).isInstanceOf(ReloadOutcome.Applied.class);
        ReloadOutcome.Applied applied = (ReloadOutcome.Applied) outcome;
        assertThat(applied.loaded().loadedAt()).isEqualTo(NOW);
        assertThat(applied.warnings()).isEmpty();
    }

    // ---- reload: every problem is reported, and nothing is opened -------------------------------

    @Test
    void aMalformedMapFileIsRejectedAndNamed() {
        Path data = validCatalogue();
        CatalogFixtures.write(data.resolve("maps"), "ridge.json", "{ not json");
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).contains("ridge.json");
    }

    @Test
    void twoMalformedMapFilesAreBothListedInOneRejection() {
        Path data = validCatalogue();
        CatalogFixtures.write(data.resolve("maps"), "ridge.json", "{ not json");
        CatalogFixtures.write(data.resolve("maps"), "dune.json", "{ not json");
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(problems).anySatisfy(line -> assertThat(line).contains("ridge.json"))
                .anySatisfy(line -> assertThat(line).contains("dune.json"));
    }

    @Test
    void aDuplicateMapNameListsBothFiles() {
        Path data = validCatalogue();
        CatalogFixtures.map(data.resolve("maps"), "ridge-copy.json", "ridge", "ridge-world");
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(String.join("\n", problems)).contains("ridge.json").contains("ridge-copy.json");
    }

    @Test
    void aCupNamingAnUnknownMapIsRejected() {
        Path data = validCatalogue();
        CatalogFixtures.cup(data.resolve("cups"), "tour.json", "tour", "RACE", "ridge", "missing-map");
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(String.join("\n", problems)).contains("missing-map");
    }

    @Test
    void anEmptyMapDirectoryIsRejected() throws IOException {
        Path data = validCatalogue();
        CatalogFixtures.cup(data.resolve("cups"), "tour.json", "tour", "RACE", "ridge");
        for (Path file : listed(data.resolve("maps"))) {
            Files.delete(file);
        }
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(problems).isNotEmpty();
    }

    @Test
    void aMissingChosenCupIsRejectedAndNamed() {
        Path data = validCatalogue();
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("summer")));

        assertThat(String.join("\n", problems)).contains("summer");
    }

    @Test
    void aMalformedUnplayedCupIsRejectedBecauseEveryProblemIsReported() {
        Path data = validCatalogue();
        CatalogFixtures.write(data.resolve("cups"), "spare.json", "{ not json");
        CatalogReloader reloader = new CatalogReloader(CLOCK, new FakeWorldOpener());

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(String.join("\n", problems)).contains("spare.json");
    }

    // ---- reload: worlds -------------------------------------------------------------------------

    @Test
    void aNewWorldIsOpenedBeforeTheReloadIsApplied() {
        Path data = validCatalogue();
        FakeWorldOpener worlds = new FakeWorldOpener("ridge-world", "dune-world");
        CatalogReloader reloader = new CatalogReloader(CLOCK, worlds);

        ReloadOutcome outcome = reloader.reload(data, Optional.of("tour"));

        assertThat(outcome).isInstanceOf(ReloadOutcome.Applied.class);
        assertThat(worlds.opened).containsExactly("ridge-world", "dune-world");
    }

    @Test
    void aWorldAlreadyOpenIsNotOpenedAgain() {
        Path data = validCatalogue();
        FakeWorldOpener worlds = new FakeWorldOpener("ridge-world", "dune-world");
        worlds.markOpen("ridge-world");
        CatalogReloader reloader = new CatalogReloader(CLOCK, worlds);

        reloader.reload(data, Optional.of("tour"));

        assertThat(worlds.opened).containsExactly("dune-world");
    }

    @Test
    void aNewMapWhoseWorldHasNoRegionDataIsRejectedNamingTheWorldAndOpensNothing() {
        Path data = validCatalogue();
        FakeWorldOpener worlds = new FakeWorldOpener("ridge-world");
        CatalogReloader reloader = new CatalogReloader(CLOCK, worlds);

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(String.join("\n", problems)).contains("dune-world");
        assertThat(worlds.opened).isEmpty();
    }

    @Test
    void aWorldThatFailsToOpenRollsBackTheWorldsThisAttemptOpened() {
        Path data = validCatalogue();
        FakeWorldOpener worlds = new FakeWorldOpener("ridge-world", "dune-world");
        worlds.failOn("dune-world");
        CatalogReloader reloader = new CatalogReloader(CLOCK, worlds);

        List<String> problems = rejectedBy(reloader.reload(data, Optional.of("tour")));

        assertThat(String.join("\n", problems)).contains("dune-world");
        assertThat(worlds.discarded).containsExactly("ridge-world");
    }

    @Test
    void anOpenWorldWhoseRegionFilesChangedIsAppliedWithARestartWarning() {
        Path data = validCatalogue();
        FakeWorldOpener worlds = new FakeWorldOpener("ridge-world", "dune-world");
        worlds.markOpen("ridge-world", "dune-world");
        worlds.markChanged("dune-world");
        CatalogReloader reloader = new CatalogReloader(CLOCK, worlds);

        ReloadOutcome outcome = reloader.reload(data, Optional.of("tour"));

        assertThat(outcome).isInstanceOf(ReloadOutcome.Applied.class);
        assertThat(((ReloadOutcome.Applied) outcome).warnings())
                .singleElement().asString().contains("restart").contains("dune-world");
        assertThat(worlds.opened).isEmpty();
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** A directory with two maps on two worlds and one cup playing both, in that order. */
    private Path validCatalogue() {
        Path data = tempDir.resolve("data");
        CatalogFixtures.map(data.resolve("maps"), "ridge.json", "ridge", "ridge-world");
        CatalogFixtures.map(data.resolve("maps"), "dune.json", "dune", "dune-world");
        CatalogFixtures.cup(data.resolve("cups"), "tour.json", "tour", "RACE", "ridge", "dune");
        return data;
    }

    private static List<String> rejectedBy(ReloadOutcome outcome) {
        assertThat(outcome).isInstanceOf(ReloadOutcome.Rejected.class);
        return ((ReloadOutcome.Rejected) outcome).problems();
    }

    private static List<Path> listed(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** A world opener that records what the reloader asked, and answers from a fixed set of worlds. */
    private static final class FakeWorldOpener implements WorldOpener {

        private final Set<String> withRegionData;
        private final Set<String> open = new HashSet<>();
        private final Set<String> changed = new HashSet<>();
        private final Set<String> failing = new HashSet<>();
        final List<String> opened = new ArrayList<>();
        final List<String> discarded = new ArrayList<>();

        FakeWorldOpener(String... withRegionData) {
            this.withRegionData = Set.of(withRegionData);
        }

        void markOpen(String... worlds) {
            open.addAll(List.of(worlds));
        }

        void markChanged(String world) {
            changed.add(world);
        }

        void failOn(String world) {
            failing.add(world);
        }

        @Override
        public boolean holdsRegionData(String world) {
            return withRegionData.contains(world) || open.contains(world);
        }

        @Override
        public boolean isOpen(String world) {
            return open.contains(world);
        }

        @Override
        public void open(String world) {
            if (failing.contains(world)) {
                throw new IllegalStateException("cannot open %s".formatted(world));
            }
            open.add(world);
            opened.add(world);
        }

        @Override
        public void discard(String world) {
            open.remove(world);
            discarded.add(world);
        }

        @Override
        public boolean regionDataChanged(String world) {
            return open.contains(world) && changed.contains(world);
        }
    }
}
