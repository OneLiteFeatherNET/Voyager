package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.race.exception.InvalidAuthorException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorTest {

    private static final UUID UUID_OF_A_BUILDER = UUID.fromString("05bf52c6-7bb0-4f13-8951-0e1fd803df35");

    @Test
    void aNameOfSixteenCharactersIsKeptAsWritten() {
        Author author = new Author(UUID_OF_A_BUILDER, "Sixteen_Chars_16");

        assertThat(author.name()).isEqualTo("Sixteen_Chars_16");
    }

    @Test
    void aNameOfSeventeenCharactersIsRefused() {
        assertThatThrownBy(() -> new Author(UUID_OF_A_BUILDER, "Seventeen_Chars_7"))
                .isInstanceOf(InvalidAuthorException.class)
                .hasMessageContaining("name");
    }

    @Test
    void aNameWithAHyphenIsRefused() {
        assertThatThrownBy(() -> new Author(UUID_OF_A_BUILDER, "Seelen-retterin"))
                .isInstanceOf(InvalidAuthorException.class);
    }

    @Test
    void aMissingUuidIsRefused() {
        assertThatThrownBy(() -> new Author(null, "TheMeinerLP"))
                .isInstanceOf(InvalidAuthorException.class)
                .hasMessageContaining("uuid");
    }

    @Test
    void aMapWithNineAuthorsIsRefused() {
        List<Author> nine = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new Author(new UUID(0L, index + 1L), "Builder" + index))
                .toList();

        assertThatThrownBy(() -> Author.checked(nine))
                .isInstanceOf(InvalidAuthorException.class)
                .hasMessageContaining("authors");
    }

    @Test
    void aMapWithTheSameUuidTwiceIsRefused() {
        List<Author> twice = List.of(
                new Author(UUID_OF_A_BUILDER, "TheMeinerLP"),
                new Author(UUID_OF_A_BUILDER, "TheMeinerLP"));

        assertThatThrownBy(() -> Author.checked(twice))
                .isInstanceOf(InvalidAuthorException.class)
                .hasMessageContaining("authors");
    }
}
