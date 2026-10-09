package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.exception.InvalidCupException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CupDefinitionAdapterTest {

    // The rotation is deliberately not in alphabetical order, so a reader that sorted it fails.
    private static final String CUP = """
            {
              "name": "test_cup",
              "mode": "PRACTICE",
              "mapNames": [ "sprint", "blue", "cathedral" ],
              "notes": [ "PROVISIONAL seed" ]
            }
            """;

    @Test
    void readsTheNameModeAndRotation() {
        CupDefinition cup = Adapters.GSON.fromJson(CUP, CupDefinition.class);

        assertThat(cup.name()).isEqualTo("test_cup");
        assertThat(cup.mode()).isEqualTo(GameMode.PRACTICE);
        assertThat(cup.mapNames()).containsExactly("sprint", "blue", "cathedral");
    }

    @Test
    void readsTheOtherModeToo() {
        // One mode in every fixture would not tell a reader of the field from a hard-coded default.
        assertThat(Adapters.GSON.fromJson(CUP.replace("PRACTICE", "RACE"), CupDefinition.class).mode())
                .isEqualTo(GameMode.RACE);
    }

    @Test
    void refusesAModeItDoesNotRecognise() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(CUP.replace("PRACTICE", "TOURNAMENT"),
                CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("cup 'test_cup' has the unrecognised mode 'TOURNAMENT'");
    }

    @Test
    void refusesACupMissingItsMode() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(CUP.replaceAll("\\s*\"mode\".*\n", "\n"),
                CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'mode'");
    }

    @Test
    void refusesARotationEntryThatIsNotAName() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(CUP.replace("\"blue\"", "{ \"name\": \"blue\" }"),
                CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("which is not a name");
    }

    @Test
    void refusesARotationThatIsNotAnArray() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                CUP.replace("[ \"sprint\", \"blue\", \"cathedral\" ]", "\"sprint\""), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be an array");
    }

    @Test
    void letsTheCupItselfRefuseAnEmptyRotation() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                CUP.replace("[ \"sprint\", \"blue\", \"cathedral\" ]", "[]"), CupDefinition.class))
                .isInstanceOf(InvalidCupException.class);
    }
}
