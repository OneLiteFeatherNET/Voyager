package net.elytrarace.voyager.api.mapsetup;

import net.elytrarace.voyager.api.mapsetup.exception.InvalidMapIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"skyfortress", "a-1_b", "0", "abcdefghijklmnopqrstuvwxyz012345"})
    void acceptsAnIdMatchingTheFolderNameRule(String value) {
        assertThat(new MapId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x", "Sky", "", "abcdefghijklmnopqrstuvwxyz0123456", "-leading", "with space", "a/b"})
    void refusesAnIdThatIsNotASafeFolderName(String value) {
        assertThatThrownBy(() -> new MapId(value))
                .as("the id %s must be refused", value)
                .isInstanceOf(InvalidMapIdException.class);
    }
}
