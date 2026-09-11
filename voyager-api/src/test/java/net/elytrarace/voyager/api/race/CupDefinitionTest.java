package net.elytrarace.voyager.api.race;

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

        assertThat(cup.name()).isEqualTo("winter-cup");
        assertThat(cup.mapNames()).containsExactlyElementsOf(MAP_NAMES);
        assertThat(cup.mode()).isEqualTo(GameMode.RACE);
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
