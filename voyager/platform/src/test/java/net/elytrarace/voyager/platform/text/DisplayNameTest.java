package net.elytrarace.voyager.platform.text;

import net.elytrarace.voyager.platform.text.exception.InvalidDisplayNameException;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayNameTest {

    private static final Key KEY = Key.key("elytrarace", "blue_and_red");

    @Test
    void acceptsNamedColourTagsOnTwoWords() {
        assertThatCode(() -> DisplayName.check("<blue>Blue</blue> & <gold>Gold</gold>")).doesNotThrowAnyException();
    }

    @Test
    void refusesAClickTagAndNamesIt() {
        assertThatThrownBy(() -> DisplayName.check("<click:run_command:/stop>Alpha</click>"))
                .isInstanceOf(InvalidDisplayNameException.class)
                .hasMessageContaining("click");
    }

    @Test
    void acceptsARedNamedColourAHexColourAGradientWithARedStopAndARainbow() {
        assertThatCode(() -> DisplayName.check("<red>Red</red>")).doesNotThrowAnyException();
        assertThatCode(() -> DisplayName.check("<#e0201a>Rush</#e0201a>")).doesNotThrowAnyException();
        assertThatCode(() -> DisplayName.check("<gradient:#e0201a:#00c2ff>Rush</gradient>")).doesNotThrowAnyException();
        assertThatCode(() -> DisplayName.check("<rainbow>Rush</rainbow>")).doesNotThrowAnyException();
    }

    @Test
    void measuresAGradientNameByItsVisibleCharactersNotItsTags() {
        assertThat(DisplayName.visibleLength("<gradient:#00c2ff:#5bff8f>Blue and Red Rush</gradient>")).isEqualTo(17);
    }

    @Test
    void acceptsANameOfExactlyFortyVisibleCharacters() {
        assertThatCode(() -> DisplayName.check("<gold>%s</gold>".formatted("x".repeat(40)))).doesNotThrowAnyException();
    }

    @Test
    void refusesAVisibleNameOfFortyOneCharactersEvenWhenItsTagsAreShort() {
        assertThatThrownBy(() -> DisplayName.check("<gold>%s</gold>".formatted("x".repeat(41))))
                .isInstanceOf(InvalidDisplayNameException.class)
                .hasMessageContaining("41")
                .hasMessageContaining("40");
    }

    @Test
    void showsALessThanSignThatIsNotATagAsLiteralText() {
        String shown = PlainTextComponentSerializer.plainText().serialize(DisplayName.component(KEY, "a < b"));

        assertThat(shown).isEqualTo("a < b");
    }
}
