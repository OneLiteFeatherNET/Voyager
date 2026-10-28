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

        assertThat(cup.key().asString()).isEqualTo("elytrarace:test_cup");
        assertThat(cup.mode()).isEqualTo(GameMode.PRACTICE);
        assertThat(cup.mapNames()).containsExactly("elytrarace:sprint", "elytrarace:blue", "elytrarace:cathedral");
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
    void readsACupWithoutSchemaVersionAsVersionOne() {
        assertThat(Adapters.GSON.fromJson(CUP, CupDefinition.class).key().asString()).isEqualTo("elytrarace:test_cup");
    }

    @Test
    void refusesCupSchemaVersionZero() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withCupSchemaVersion("0"), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'schemaVersion' must be a whole number of 1 or more");
    }

    @Test
    void refusesCupSchemaVersionAboveTheSupportedMaximumNamingBothNumbers() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withCupSchemaVersion("3"), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("cup 'test_cup' declares schemaVersion 3")
                .hasMessageContaining("up to schemaVersion 2");
    }

    private static String withCupSchemaVersion(String value) {
        return CUP.replaceFirst("\\{", "{\n  \"schemaVersion\": " + value + ",");
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
    void aVersionOneFlatNameIsReadAsAKeyInTheElytraraceNamespace() {
        CupDefinition cup = Adapters.GSON.fromJson(CUP.replace("test_cup", "palm_cup"), CupDefinition.class);

        assertThat(cup.key().asString()).isEqualTo("elytrarace:palm_cup");
    }

    @Test
    void aVersionTwoCupReadsItsKeyAndNoName() {
        CupDefinition cup = Adapters.GSON.fromJson(withCupSchemaVersion("2")
                .replace("\"name\": \"test_cup\"", "\"key\": \"elytrarace:alpha_cup\""), CupDefinition.class);

        assertThat(cup.key().asString()).isEqualTo("elytrarace:alpha_cup");
    }

    @Test
    void aVersionTwoCupWithAKeyInAnotherNamespaceIsRefusedNamingTheKey() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withCupSchemaVersion("2")
                        .replace("\"name\": \"test_cup\"", "\"key\": \"other:alpha_cup\""), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'key'");
    }

    @Test
    void aVersionOneFlatNameWithCapitalsIsRefusedNamingTheField() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(CUP.replace("test_cup", "TestCup"), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'name'");
    }

    @Test
    void aVersionTwoCupThatStatesBothKeyAndNameIsRefusedNamingTheName() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withCupSchemaVersion("2")
                        .replaceFirst("\"name\": \"test_cup\",", "\"name\": \"test_cup\", \"key\": \"elytrarace:test_cup\","),
                CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'name'");
    }

    @Test
    void aVersionTwoCupWithoutAKeyIsRefusedNamingTheKey() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withCupSchemaVersion("2")
                        .replace("\"name\": \"test_cup\",", ""), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'key'");
    }

    @Test
    void readsTheAuthorsOfACupWithTheirUuidInLowerCase() {
        CupDefinition cup = Adapters.GSON.fromJson(CUP.replaceFirst("\\{", "{\n  \"authors\": [ { \"uuid\": "
                + "\"05BF52C6-7BB0-4F13-8951-0E1FD803DF35\", \"name\": \"TheMeinerLP\" } ],"), CupDefinition.class);

        assertThat(cup.authors()).singleElement()
                .satisfies(author -> {
                    assertThat(author.uuid().toString()).isEqualTo("05bf52c6-7bb0-4f13-8951-0e1fd803df35");
                    assertThat(author.name()).isEqualTo("TheMeinerLP");
                });
    }

    @Test
    void anAuthorWhoseUuidIsNotAUuidIsRefusedNamingTheField() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(CUP.replaceFirst("\\{", "{\n  \"authors\": [ { \"uuid\": "
                + "\"not-a-uuid\", \"name\": \"TheMeinerLP\" } ],"), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'uuid'");
    }

    @Test
    void aCupWhoseAuthorsRepeatAUuidIsRefusedNamingTheField() {
        String author = "{ \"uuid\": \"05bf52c6-7bb0-4f13-8951-0e1fd803df35\", \"name\": \"TheMeinerLP\" }";
        assertThatThrownBy(() -> Adapters.GSON.fromJson(CUP.replaceFirst("\\{",
                        "{\n  \"authors\": [ " + author + ", " + author + " ],"), CupDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'authors'");
    }

    @Test
    void letsTheCupItselfRefuseAnEmptyRotation() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                CUP.replace("[ \"sprint\", \"blue\", \"cathedral\" ]", "[]"), CupDefinition.class))
                .isInstanceOf(InvalidCupException.class);
    }
}
