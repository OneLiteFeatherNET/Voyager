package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.exception.InvalidRingException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RingAdapterTest {

    // Index 4 and 25 points, so neither can be confused with the other or with a position in a
    // list; a tilted normal, so no component is zero; a radius that is not a whole number.
    private static final String RING = """
            {
              "index": 4,
              "center": { "x": 2.0, "y": -31.0, "z": 69.0 },
              "normal": { "x": -0.6666666666666666, "y": 0.6666666666666666, "z": -0.3333333333333333 },
              "radius": 3.605551275463989,
              "points": 25,
              "type": "BOOST"
            }
            """;

    /** Reads the fixture as the ring at position 4, which is the index it declares. */
    private static Ring read(String json) {
        return RingAdapter.read(JsonParser.parseString(json), 4);
    }

    @Test
    void readsEveryFieldOfARing() {
        Ring ring = read(RING);

        assertThat(ring.index()).isEqualTo(4);
        assertThat(ring.center()).isEqualTo(new Vec3(2, -31, 69));
        assertThat(ring.normal().x()).isEqualTo(-2.0 / 3);
        assertThat(ring.normal().y()).isEqualTo(2.0 / 3);
        assertThat(ring.normal().z()).isEqualTo(-1.0 / 3);
        assertThat(ring.radius()).isEqualTo(Math.sqrt(13));
        assertThat(ring.points()).isEqualTo(25);
        assertThat(ring.type()).isEqualTo(RingType.BOOST);
    }

    @Test
    void takesTheIndexFromThePositionWhenTheFileLeavesItOut() {
        Ring ring = RingAdapter.read(JsonParser.parseString(RING.replace("\"index\": 4,", "")), 7);

        assertThat(ring.index()).isEqualTo(7);
    }

    @Test
    void refusesADeclaredIndexThatIsNotThePosition() {
        assertThatThrownBy(() -> RingAdapter.read(JsonParser.parseString(RING), 3))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("ring at position 3 declares index 4")
                .hasMessageContaining("or be left out");
    }

    @Test
    void readsTheStoredNormalRatherThanDerivingOne() {
        // D-E3-5: the normal is stored, and its sign is what decides which way through the ring
        // counts. Reading a ring whose normal points the opposite way from the obvious guess and
        // getting that value back is what says the file is believed.
        Ring ring = read(
                RING.replace("-0.6666666666666666", "0.6666666666666666")
                        .replaceFirst("\"y\": 0.6666666666666666", "\"y\": -0.6666666666666666"));

        assertThat(ring.normal().x()).isEqualTo(2.0 / 3);
        assertThat(ring.normal().y()).isEqualTo(-2.0 / 3);
    }

    @Test
    void refusesARingTypeItDoesNotRecognise() {
        // Refused, not defaulted to STANDARD the way the old loader did. A misspelt BOOST that
        // becomes a STANDARD is a course that scores correctly and plays wrong.
        assertThatThrownBy(() -> read(RING.replace("BOOST", "TURBO")))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("ring 4 has the unrecognised type 'TURBO'");
    }

    @Test
    void refusesARingMissingItsRadius() {
        assertThatThrownBy(() -> read(RING.replaceAll("\\s*\"radius\".*\n", "\n")))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("ring 4 is missing the field 'radius'");
    }

    @Test
    void refusesARingMissingItsNormal() {
        assertThatThrownBy(() -> read(RING.replaceAll("\\s*\"normal\".*\n", "\n")))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("ring 4 is missing the field 'normal'");
    }

    @Test
    void letsTheRingItselfRefuseANormalThatIsNotUnitLength() {
        // The adapter does not re-check the geometry; Ring's own constructor does, and this is what
        // says the adapter hands it the value unaltered instead of normalising it on the way past.
        assertThatThrownBy(() -> read(RING.replace("-0.3333333333333333", "-0.9")))
                .isInstanceOf(InvalidRingException.class);
    }

    @Test
    void refusesAFractionalIndex() {
        assertThatThrownBy(() -> read(RING.replace("\"index\": 4", "\"index\": 4.5")))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("must be a whole number");
    }
}
