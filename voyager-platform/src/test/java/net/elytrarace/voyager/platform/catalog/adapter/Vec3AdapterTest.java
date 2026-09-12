package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Vec3AdapterTest {

    @Test
    void readsEachComponentIntoItsOwnAxis() {
        // Three different magnitudes with two different signs: a reader that swapped two axes, or
        // dropped a sign, cannot produce this.
        Vec3 vector = Adapters.GSON.fromJson("{\"x\": 109.0, \"y\": -62.5, \"z\": 54.0}", Vec3.class);

        assertThat(vector.x()).isEqualTo(109.0);
        assertThat(vector.y()).isEqualTo(-62.5);
        assertThat(vector.z()).isEqualTo(54.0);
    }

    @Test
    void refusesAMissingComponentRatherThanDefaultingItToZero() {
        // The whole reason these adapters are written by hand. Gson's reflective binding leaves an
        // absent y at 0.0, which is a perfectly ordinary height, so the mistake survives every
        // check downstream and only shows as a ring in the wrong place.
        assertThatThrownBy(() -> Adapters.GSON.fromJson("{\"x\": 1.0, \"z\": 3.0}", Vec3.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("'y'");
    }

    @Test
    void refusesANullComponent() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson("{\"x\": 1.0, \"y\": null, \"z\": 3.0}", Vec3.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("'y'");
    }

    @Test
    void refusesAComponentThatIsNotANumber() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson("{\"x\": 1.0, \"y\": \"2\", \"z\": 3.0}", Vec3.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be a number");
    }

    @Test
    void refusesAVectorWrittenAsAnArray() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson("[1.0, 2.0, 3.0]", Vec3.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be an object");
    }
}
