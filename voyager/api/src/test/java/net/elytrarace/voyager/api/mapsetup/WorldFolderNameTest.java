package net.elytrarace.voyager.api.mapsetup;

import net.elytrarace.voyager.api.mapsetup.exception.InvalidWorldFolderNameException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldFolderNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"elytrarace-cube", "Team_Build.2", "a", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void acceptsNamesOfTheAllowedCharactersAndLength(String name) {
        assertThat(new WorldFolderName(name).value()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", ".", "../secret", "a/b", "a\\b", ""})
    void refusesTraversalSeparatorsAndEmptyNames(String name) {
        assertThatThrownBy(() -> new WorldFolderName(name))
                .as("the name '%s' must be refused", name)
                .isInstanceOf(InvalidWorldFolderNameException.class);
    }

    @Test
    void refusesANameOfSixtyFiveCharacters() {
        assertThatThrownBy(() -> new WorldFolderName("x".repeat(65)))
                .isInstanceOf(InvalidWorldFolderNameException.class);
    }
}
