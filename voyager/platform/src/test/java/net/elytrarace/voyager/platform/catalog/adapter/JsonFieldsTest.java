package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonFieldsTest {

    private static final JsonObject OBJECT = JsonParser.parseString("""
            {
              "name": "blue",
              "count": 35,
              "radius": 3.605551275463989,
              "fraction": 4.5,
              "rings": [ 1, 2 ],
              "nested": { "x": 1 },
              "nothing": null
            }
            """).getAsJsonObject();

    @Test
    void readsEachKindOfFieldAsItsOwnType() {
        assertThat(JsonFields.string(OBJECT, "name", "a map")).isEqualTo("blue");
        assertThat(JsonFields.integer(OBJECT, "count", "a map")).isEqualTo(35);
        assertThat(JsonFields.number(OBJECT, "radius", "a map")).isEqualTo(Math.sqrt(13));
        assertThat(JsonFields.array(OBJECT, "rings", "a map")).hasSize(2);
        assertThat(JsonFields.object(OBJECT.get("nested"), "a vector").get("x").getAsInt()).isEqualTo(1);
    }

    @Test
    void namesBothTheSubjectAndTheFieldWhenOneIsAbsent() {
        // "a map is missing the field 'world'" is one line an operator can act on. "NullPointer" is
        // not, and neither is a defaulted value that never says anything at all.
        assertThatThrownBy(() -> JsonFields.string(OBJECT, "world", "map 'blue'"))
                .isInstanceOf(JsonParseException.class)
                .hasMessage("map 'blue' is missing the field 'world'");
    }

    @Test
    void treatsAnExplicitNullAsAbsent() {
        assertThatThrownBy(() -> JsonFields.number(OBJECT, "nothing", "a map"))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("is missing the field 'nothing'");
    }

    @Test
    void refusesANumberWrittenAsAString() {
        // Gson's own coercion would accept "3.6" here. Accepting it is how a file drifts into a
        // format nothing declares, and one that a stricter reader later rejects at the worst moment.
        assertThatThrownBy(() -> JsonFields.number(OBJECT, "name", "a map"))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be a number");
    }

    @Test
    void refusesAStringWrittenAsANumber() {
        assertThatThrownBy(() -> JsonFields.string(OBJECT, "count", "a map"))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be a string");
    }

    @Test
    void refusesAWholeNumberFieldHoldingAFraction() {
        // A ring index of 4.5 truncated to 4 would silently duplicate ring 4 and lose ring 5.
        assertThatThrownBy(() -> JsonFields.integer(OBJECT, "fraction", "a map"))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be a whole number");
    }

    @Test
    void refusesAnArrayFieldThatIsNotAnArray() {
        assertThatThrownBy(() -> JsonFields.array(OBJECT, "nested", "a map"))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be an array");
    }

    @Test
    void refusesAnObjectThatIsAPrimitive() {
        assertThatThrownBy(() -> JsonFields.object(OBJECT.get("count"), "a vector"))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("a vector must be an object");
    }
}
