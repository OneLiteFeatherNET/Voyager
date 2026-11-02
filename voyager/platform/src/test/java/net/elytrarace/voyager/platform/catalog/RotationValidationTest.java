package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.api.config.ConfigProblem.Severity;
import net.elytrarace.voyager.platform.rotation.RotationValidation;
import net.elytrarace.voyager.platform.world.WorldHealth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boot check over every cup of a rotation: a definition error refuses, a missing world skips its cup with one
 * warning, a damaged world refuses, and a cup outside the rotation never refuses.
 *
 * <p>Each test writes its own catalogue and worlds into its own temporary directory. The world health is a fake that
 * records the order it was asked in, so the one-cup-at-a-time order is observed rather than assumed. The world names
 * are distinct from the cup names, and the cups play distinct worlds, so a check that read the wrong cup's world would
 * not pass.
 */
class RotationValidationTest {

    private static final String ALPHA_MAP = "alpha_map";
    private static final String LEGACY_MAP = "palm_map";

    @TempDir
    Path root;

    private Path data;
    private Path worlds;
    private Path cups;
    private final List<String> asked = new ArrayList<>();

    @Test
    void rotationCupsWithCompleteWorldsArePlayableInTheListedOrder() throws IOException {
        completeCatalogue();

        RotationValidation.Result result = check(List.of("elytrarace:legacy_cup", "elytrarace:alpha_cup"), ok());

        assertThat(result.playable()).containsExactly("elytrarace:legacy_cup", "elytrarace:alpha_cup");
        assertThat(result.skipped()).isEmpty();
        assertThat(result.refused()).isFalse();
        assertThat(result.problems()).isEmpty();
    }

    @Test
    void aCupWithAMissingWorldIsSkippedWithOneWarningThatNamesTheCupAndItsWorlds() throws IOException {
        completeCatalogue();
        Files.delete(worlds.resolve("PalmBeach").resolve("region").resolve("r.0.0.mca"));
        Files.delete(worlds.resolve("PalmBeach").resolve("region"));
        Files.delete(worlds.resolve("PalmBeach"));

        RotationValidation.Result result = check(List.of("elytrarace:alpha_cup", "elytrarace:legacy_cup"), ok());

        assertThat(result.skipped()).containsExactly("elytrarace:legacy_cup");
        assertThat(result.playable()).containsExactly("elytrarace:alpha_cup");
        assertThat(result.refused()).isFalse();
        assertThat(result.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.severity()).isEqualTo(Severity.WARNING);
            assertThat(problem.message()).contains("elytrarace:legacy_cup").contains("PalmBeach");
        });
    }

    @Test
    void aDamagedWorldOfARotationCupRefusesBootAndNamesTheWorld() throws IOException {
        completeCatalogue();

        RotationValidation.Result result = check(List.of("elytrarace:alpha_cup", "elytrarace:legacy_cup"),
                world -> world.equals("ElytraraceBlueAndRed") ? damaged(world) : sound(world));

        assertThat(result.refused()).isTrue();
        assertThat(errorsOf(result)).singleElement().satisfies(problem ->
                assertThat(problem.message()).contains("ElytraraceBlueAndRed"));
    }

    @Test
    void whenNoRotationCupIsPlayableBootIsRefusedAndTheRefusalListsTheCupAndItsWorlds() throws IOException {
        completeCatalogue();
        Files.delete(worlds.resolve("PalmBeach").resolve("region").resolve("r.0.0.mca"));
        Files.delete(worlds.resolve("PalmBeach").resolve("region"));
        Files.delete(worlds.resolve("PalmBeach"));

        RotationValidation.Result result = check(List.of("elytrarace:legacy_cup"), ok());

        assertThat(result.playable()).isEmpty();
        assertThat(result.refused()).isTrue();
        assertThat(errorsOf(result)).singleElement().satisfies(problem -> assertThat(problem.message())
                .contains("elytrarace:legacy_cup").contains("PalmBeach"));
    }

    @Test
    void aDefinitionErrorInARotationCupRefusesBoot() throws IOException {
        completeCatalogue();
        CatalogFixtures.cup(cups, "broken.json", "broken_cup", "RACE", "no_such_map");

        RotationValidation.Result result = check(List.of("elytrarace:broken_cup", "elytrarace:alpha_cup"), ok());

        assertThat(result.refused()).isTrue();
        assertThat(errorsOf(result)).isNotEmpty();
    }

    @Test
    void aMalformedCupOutsideTheRotationDoesNotRefuseBoot() throws IOException {
        completeCatalogue();
        Files.writeString(cups.resolve("stray.json"), "{ not json");

        RotationValidation.Result result = check(List.of("elytrarace:alpha_cup"), ok());

        assertThat(result.refused()).isFalse();
        assertThat(result.playable()).containsExactly("elytrarace:alpha_cup");
        assertThat(errorsOf(result)).isEmpty();
    }

    @Test
    void theWorldsOfEveryRotationCupAreCheckedInTheOrderOfTheRotation() throws IOException {
        completeCatalogue();

        check(List.of("elytrarace:legacy_cup", "elytrarace:alpha_cup"), ok());

        assertThat(asked).containsExactly("PalmBeach", "DragonLobby", "Memoria", "ElytraraceBlueAndRed");
    }

    @Test
    void aCupNamedInTheKeyFormResolvesToTheSameCupAsTheFlatId() throws IOException {
        completeCatalogue();

        RotationValidation.Result keyed = check(List.of("elytrarace:alpha_cup"), ok());
        RotationValidation.Result flat = check(List.of("alpha_cup"), ok());

        assertThat(keyed.playable()).containsExactly("elytrarace:alpha_cup");
        assertThat(flat.playable()).isEqualTo(keyed.playable());
    }

    @Test
    void aCupNamedTwiceIsRefusedWhetherOrNotItIsSpelledAsAKey() throws IOException {
        completeCatalogue();

        RotationValidation.Result result = check(List.of("alpha_cup", "elytrarace:alpha_cup"), ok());

        assertThat(result.refused()).isTrue();
        assertThat(errorsOf(result)).singleElement().satisfies(problem ->
                assertThat(problem.message()).contains("elytrarace:alpha_cup"));
    }

    @Test
    void anUnknownCupNameRefusesAndIsNamed() throws IOException {
        completeCatalogue();

        RotationValidation.Result result = check(List.of("alpha_cup", "alhpa"), ok());

        assertThat(result.refused()).isTrue();
        assertThat(errorsOf(result)).singleElement().satisfies(problem ->
                assertThat(problem.message()).contains("alhpa"));
    }

    /** Writes a catalogue of two cups and their maps, and the worlds both cups play: every world is complete. */
    private void completeCatalogue() throws IOException {
        data = Files.createDirectories(root.resolve("data"));
        worlds = Files.createDirectories(root.resolve("worlds"));
        cups = data.resolve("cups");
        CatalogFixtures.map(data.resolve("maps"), "alpha.json", ALPHA_MAP, "ElytraraceBlueAndRed");
        CatalogFixtures.map(data.resolve("maps"), "palm.json", LEGACY_MAP, "PalmBeach");
        CatalogFixtures.map(data.resolve("maps"), "dragon.json", "dragon_map", "DragonLobby");
        CatalogFixtures.map(data.resolve("maps"), "memoria.json", "memoria_map", "Memoria");
        CatalogFixtures.cup(cups, "alpha_cup.json", "alpha_cup", "RACE", ALPHA_MAP);
        CatalogFixtures.cup(cups, "legacy_cup.json", "legacy_cup", "RACE", LEGACY_MAP,
                "dragon_map", "memoria_map");
        for (String world : List.of("ElytraraceBlueAndRed", "PalmBeach", "DragonLobby", "Memoria")) {
            Path region = Files.createDirectories(worlds.resolve(world).resolve("region"));
            Files.writeString(region.resolve("r.0.0.mca"), "");
        }
    }

    private RotationValidation.Result check(List<String> requested, CatalogValidation.HealthSource health) {
        CatalogReading reading = CatalogLoader.read(data);
        return RotationValidation.check(reading, requested, worlds, cups.toAbsolutePath(), world -> {
            asked.add(world);
            return health.healthOf(world);
        });
    }

    private static CatalogValidation.HealthSource ok() {
        return RotationValidationTest::sound;
    }

    private static WorldHealth sound(String world) {
        return new WorldHealth(world, 100, 0, 0, 0, Map.of(), 0);
    }

    private static WorldHealth damaged(String world) {
        return new WorldHealth(world, 100, 0, 0, 0, Map.of(), 3);
    }

    private static List<ConfigProblem> errorsOf(RotationValidation.Result result) {
        return result.problems().stream().filter(problem -> problem.severity() == Severity.ERROR).toList();
    }
}
