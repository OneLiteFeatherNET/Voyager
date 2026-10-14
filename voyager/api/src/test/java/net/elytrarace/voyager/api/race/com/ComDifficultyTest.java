package net.elytrarace.voyager.api.race.com;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComDifficultyTest {

    @Test
    void theNamesEASYMEDIUMandHARDAreListedInThatOrder() {
        assertThat(ComDifficulty.values())
                .as("the difficulty names, in the order the settings document them")
                .containsExactly(ComDifficulty.EASY, ComDifficulty.MEDIUM, ComDifficulty.HARD);
    }

    @Test
    void byNameIsCaseInsensitive() {
        assertThat(ComDifficulty.byName("hard"))
                .as("the lower-case name of HARD")
                .contains(ComDifficulty.HARD);
    }

    @Test
    void anUnknownNameIsEmpty() {
        assertThat(ComDifficulty.byName("expert"))
                .as("a name that is not one of EASY, MEDIUM or HARD")
                .isEmpty();
    }
}
