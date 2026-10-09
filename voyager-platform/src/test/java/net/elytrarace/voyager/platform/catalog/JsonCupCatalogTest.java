package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonCupCatalogTest {

    @Test
    void readsACupUnderTheNameInsideTheFileRatherThanTheFilename(@TempDir Path cups) {
        CatalogFixtures.cup(cups, "01-cup.json", "test_cup", "RACE", "blue");

        JsonCupCatalog catalog = new JsonCupCatalog(cups);

        assertThat(catalog.byName("test_cup")).isPresent();
        assertThat(catalog.byName("01-cup")).isEmpty();
    }

    @Test
    void keepsTheRotationInFileOrder(@TempDir Path cups) {
        // Names chosen so that alphabetical order and file order disagree: a reader that sorted the
        // rotation would put "blue" first, and the cup would play its maps in the wrong sequence
        // while every name in it still resolved.
        CatalogFixtures.cup(cups, "cup.json", "test_cup", "PRACTICE", "sprint", "blue", "cathedral");

        CupDefinition cup = new JsonCupCatalog(cups).byName("test_cup").orElseThrow();

        assertThat(cup.mapNames()).containsExactly("sprint", "blue", "cathedral");
        assertThat(cup.mode()).isEqualTo(GameMode.PRACTICE);
    }

    @Test
    void readsEveryModeTheEnumOffers(@TempDir Path cups) {
        CatalogFixtures.cup(cups, "race.json", "ranked", "RACE", "blue");
        CatalogFixtures.cup(cups, "practice.json", "casual", "PRACTICE", "blue");

        JsonCupCatalog catalog = new JsonCupCatalog(cups);

        assertThat(catalog.cupNames()).containsExactlyInAnyOrder("ranked", "casual");
        assertThat(catalog.byName("ranked").orElseThrow().mode()).isEqualTo(GameMode.RACE);
        assertThat(catalog.byName("casual").orElseThrow().mode()).isEqualTo(GameMode.PRACTICE);
    }

    @Test
    void answersEmptyForACupItDoesNotKnow(@TempDir Path cups) {
        CatalogFixtures.cup(cups, "cup.json", "test_cup", "RACE", "blue");

        assertThat(new JsonCupCatalog(cups).byName("weekly")).isEmpty();
    }

    @Test
    void refusesAModeItDoesNotRecognise(@TempDir Path cups) {
        // Refused, not defaulted to RACE. A cup silently promoted into the ranked mode writes a
        // practice session into the records.
        CatalogFixtures.cup(cups, "cup.json", "test_cup", "TOURNAMENT", "blue");

        assertThatThrownBy(() -> new JsonCupCatalog(cups))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("unrecognised mode 'TOURNAMENT'");
    }

    @Test
    void refusesACupThatPlaysNoMaps(@TempDir Path cups) {
        CatalogFixtures.cup(cups, "cup.json", "test_cup", "RACE");

        assertThatThrownBy(() -> new JsonCupCatalog(cups))
                .isInstanceOf(MalformedCatalogFileException.class)
                .hasMessageContaining("cup.json");
    }

    @Test
    void refusesADirectoryThatIsNotThere(@TempDir Path root) {
        assertThatThrownBy(() -> new JsonCupCatalog(root.resolve("cups")))
                .isInstanceOf(UnreadableCatalogException.class)
                .hasMessageContaining("cup catalogue directory");
    }

    @Test
    void refusesADirectoryWithNoDefinitionsInIt(@TempDir Path cups) {
        CatalogFixtures.write(cups, "cups.yaml", "- name: test_cup");

        assertThatThrownBy(() -> new JsonCupCatalog(cups))
                .isInstanceOf(UnreadableCatalogException.class)
                .hasMessageContaining("holds no .json file");
    }
}
