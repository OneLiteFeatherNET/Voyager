package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidAuthorException;
import net.elytrarace.voyager.api.race.exception.InvalidCatalogKeyException;
import net.elytrarace.voyager.api.race.exception.InvalidCupException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CupDefinitionTest {

    private static final List<String> MAP_NAMES = List.of("nether-sprint", "sky-loop", "canyon-run");

    @Test
    void keepsWhatItWasGiven() {
        CupDefinition cup = new CupDefinition("winter-cup", MAP_NAMES, GameMode.RACE);

        assertThat(cup.key().asString()).isEqualTo("elytrarace:winter-cup");
        assertThat(cup.mapNames()).containsExactly("elytrarace:nether-sprint", "elytrarace:sky-loop", "elytrarace:canyon-run");
        assertThat(cup.mode()).isEqualTo(GameMode.RACE);
    }

    @Test
    void aCupWithoutADisplayNameShowsItsKeyValueInTitleCase() {
        CupDefinition cup = new CupDefinition("winter-cup", MAP_NAMES, GameMode.RACE);

        assertThat(cup.displayName()).isEqualTo("Winter Cup");
    }

    @Test
    void aBlankDisplayNameShowsItsKeyValueInTitleCase() {
        CupDefinition cup = new CupDefinition("winter-cup", MAP_NAMES, GameMode.RACE, "   ");

        assertThat(cup.displayName()).isEqualTo("Winter Cup");
    }

    @Test
    void aCupKeyWithUnderscoresShowsItsValueWithSpacesInTitleCase() {
        CupDefinition cup = new CupDefinition("dragon_lobby", MAP_NAMES, GameMode.RACE);

        assertThat(cup.displayName()).isEqualTo("Dragon Lobby");
    }

    @Test
    void keepsTheDisplayNameItIsGiven() {
        CupDefinition cup = new CupDefinition("winter-cup", MAP_NAMES, GameMode.RACE, "Winter Cup");

        assertThat(cup.displayName()).isEqualTo("Winter Cup");
        assertThat(cup.key().asString()).isEqualTo("elytrarace:winter-cup");
    }

    @Test
    void aCupBuiltWithAKeyReturnsThatKey() {
        CupDefinition cup = new CupDefinition(CatalogKeys.of("alpha_cup"), MAP_NAMES, GameMode.RACE, "Alpha Cup",
                List.of());

        assertThat(cup.key().asString()).isEqualTo("elytrarace:alpha_cup");
        assertThat(cup.key().namespace()).isEqualTo("elytrarace");
    }

    @Test
    void aCupKeyInAnotherNamespaceIsRefused() {
        assertThatThrownBy(() -> new CupDefinition(net.kyori.adventure.key.Key.key("other", "alpha_cup"),
                MAP_NAMES, GameMode.RACE, null, List.of()))
                .isInstanceOf(InvalidCatalogKeyException.class)
                .hasMessageContaining("key");
    }

    @Test
    void aCupWithoutAuthorsHasNone() {
        CupDefinition cup = new CupDefinition("winter-cup", MAP_NAMES, GameMode.RACE);

        assertThat(cup.authors()).isEmpty();
    }

    @Test
    void aCupKeepsItsAuthorsInTheOrderGiven() {
        Author first = new Author(java.util.UUID.fromString("05bf52c6-7bb0-4f13-8951-0e1fd803df35"), "TheMeinerLP");
        Author second = new Author(java.util.UUID.fromString("e1dff87a-1059-47f1-bc7d-533f072bf8c7"), "Seelenretterin");

        CupDefinition cup = new CupDefinition(CatalogKeys.of("alpha_cup"), MAP_NAMES, GameMode.RACE, null,
                List.of(first, second));

        assertThat(cup.authors()).containsExactly(first, second);
    }

    @Test
    void aCupWithNineAuthorsIsRefused() {
        List<Author> nine = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new Author(new java.util.UUID(0L, index + 1L), "Builder" + index))
                .toList();

        assertThatThrownBy(() -> new CupDefinition(CatalogKeys.of("alpha_cup"), MAP_NAMES, GameMode.RACE, null, nine))
                .isInstanceOf(InvalidAuthorException.class)
                .hasMessageContaining("authors");
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> new CupDefinition("  ", MAP_NAMES, GameMode.RACE))
                .isInstanceOf(InvalidCupException.class);
    }

    @Test
    void rejectsAnEmptyMapList() {
        assertThatThrownBy(() -> new CupDefinition("winter-cup", List.of(), GameMode.RACE))
                .isInstanceOf(InvalidCupException.class);
    }

    /**
     * A null in either of the two components that were previously unchecked. The package is
     * {@code @NotNullByDefault}, so this is defence against a deserialiser rather than against a
     * caller who can read — but a record that checks one component and trusts the next is the worst
     * of both, which is what these pin.
     */
    @Test
    void rejectsANullMapListAndANullModeTheSameWayItRejectsANullName() {
        assertThatThrownBy(() -> new CupDefinition("winter-cup", null, GameMode.RACE))
                .isInstanceOf(InvalidCupException.class);
        assertThatThrownBy(() -> new CupDefinition("winter-cup", MAP_NAMES, null))
                .isInstanceOf(InvalidCupException.class);
        assertThatThrownBy(() -> new CupDefinition(null, MAP_NAMES, GameMode.RACE))
                .isInstanceOf(InvalidCupException.class);
    }

    @Test
    void copiesItsMapListSoACallerCannotChangeItAfterwards() {
        List<String> mutable = new ArrayList<>(MAP_NAMES);
        CupDefinition cup = new CupDefinition("winter-cup", mutable, GameMode.RACE);

        mutable.clear();

        assertThat(cup.mapNames()).hasSize(3);
    }

    @Test
    void gameModeCarriesTheValuesFromTheOldTree() {
        assertThat(GameMode.RACE.minimumPlayers()).isEqualTo(2);
        assertThat(GameMode.RACE.ranked()).isTrue();
        assertThat(GameMode.PRACTICE.minimumPlayers()).isEqualTo(1);
        assertThat(GameMode.PRACTICE.ranked()).isFalse();
    }

    @Test
    void gameModeByNameIsCaseInsensitiveAndReportsAbsence() {
        assertThat(GameMode.byName("practice")).contains(GameMode.PRACTICE);
        assertThat(GameMode.byName("spectator")).isEmpty();
    }
}
