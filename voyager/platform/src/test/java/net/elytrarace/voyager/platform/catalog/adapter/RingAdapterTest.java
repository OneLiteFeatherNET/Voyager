package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingShape;
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

    private static final String FILE = "map 'sky-drift'";

    /** Reads the fixture as the ring at position 4 of a version 2 file, which is the index it declares. */
    private static Ring read(String json) {
        return RingAdapter.read(JsonParser.parseString(json), 4, 2, FILE);
    }

    /** Reads an oval ring at position {@code position} of a file of {@code version}. */
    private static Ring readOval(String json, int position, int version) {
        return RingAdapter.read(JsonParser.parseString(json), position, version, FILE);
    }

    /** An axis-aligned oval facing +y: semi-major radius 6 along x, semi-minor radius 3 along z. */
    private static final String OVAL = """
            {
              "center": { "x": 2.0, "y": -31.0, "z": 69.0 },
              "normal": { "x": 0.0, "y": 1.0, "z": 0.0 },
              "radius": 6.0,
              "radiusMinor": 3.0,
              "majorAxis": { "x": 1.0, "y": 0.0, "z": 0.0 },
              "points": 25,
              "type": "STANDARD"
            }
            """;

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
        Ring ring = RingAdapter.read(JsonParser.parseString(RING.replace("\"index\": 4,", "")), 7, 2, FILE);

        assertThat(ring.index()).isEqualTo(7);
    }

    @Test
    void refusesADeclaredIndexThatIsNotThePosition() {
        assertThatThrownBy(() -> RingAdapter.read(JsonParser.parseString(RING), 3, 2, FILE))
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

    @Test
    void readsAVersionThreeOvalRingAsAnOval() {
        Ring ring = readOval(OVAL, 4, 3);

        assertThat(ring.shape()).isEqualTo(new RingShape.Ellipse(6.0, 3.0, new Vec3(1.0, 0.0, 0.0)));
        assertThat(ring.radius()).isEqualTo(6.0);
    }

    @Test
    void readsAVersionThreeFileOfCirclesWithEveryRingACircle() {
        Ring ring = RingAdapter.read(JsonParser.parseString(RING), 4, 3, FILE);

        assertThat(ring.shape()).isEqualTo(new RingShape.Circle(3.605551275463989));
    }

    @Test
    void readsEqualRadiiAsACircleOfThatRadius() {
        Ring ring = readOval(OVAL.replace("\"radiusMinor\": 3.0", "\"radiusMinor\": 6.0"), 4, 3);

        assertThat(ring.shape()).isEqualTo(new RingShape.Circle(6.0));
    }

    @Test
    void refusesAnOvalInAVersionTwoFileNamingTheFileThePositionAndTheField() {
        assertThatThrownBy(() -> readOval(OVAL, 2, 2))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("map 'sky-drift'")
                .hasMessageContaining("ring at position 2")
                .hasMessageContaining("radiusMinor")
                .hasMessageContaining("schemaVersion 3");
    }

    @Test
    void refusesAnOvalInAnUnversionedFileAsVersionOne() {
        assertThatThrownBy(() -> readOval(OVAL, 0, 1))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("schemaVersion 3")
                .hasMessageContaining("this file is schemaVersion 1");
    }

    @Test
    void refusesRadiusMinorWithoutMajorAxisNamingThePositionAndTheMissingField() {
        assertThatThrownBy(() -> readOval(OVAL.replaceAll("\\s*\"majorAxis\".*\n", "\n"), 2, 3))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("ring at position 2")
                .hasMessageContaining("'radiusMinor' but not 'majorAxis'");
    }

    @Test
    void refusesMajorAxisWithoutRadiusMinorNamingThePositionAndTheMissingField() {
        assertThatThrownBy(() -> readOval(OVAL.replaceAll("\\s*\"radiusMinor\".*\n", "\n"), 0, 3))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("ring at position 0")
                .hasMessageContaining("'majorAxis' but not 'radiusMinor'");
    }

    @Test
    void refusesAMinorRadiusAboveTheMajorNamingTheFilePositionAndField() {
        assertThatThrownBy(() -> readOval(OVAL.replace("\"radiusMinor\": 3.0", "\"radiusMinor\": 7.0"), 1, 3))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("map 'sky-drift', ring at position 1")
                .hasMessageContaining("radiusMinor");
    }

    @Test
    void refusesAnOvalWhoseAxisIsParallelToItsNormalNamingMajorAxis() {
        assertThatThrownBy(() -> readOval(OVAL.replace("\"majorAxis\": { \"x\": 1.0, \"y\": 0.0, \"z\": 0.0 }",
                "\"majorAxis\": { \"x\": 0.0, \"y\": 1.0, \"z\": 0.0 }"), 0, 3))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("ring at position 0")
                .hasMessageContaining("majorAxis");
    }

    @Test
    void refusesARadiusBelowTheBoundsNamingThePositionAndTheRadius() {
        assertThatThrownBy(() -> RingAdapter.read(JsonParser.parseString(RING.replace("3.605551275463989", "0.25")),
                4, 2, FILE))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("ring at position 4")
                .hasMessageContaining("radius")
                .hasMessageContaining("0.25");
    }

    @Test
    void refusesMoreThanOneHundredPointsNamingThePosition() {
        assertThatThrownBy(() -> read(RING.replace("\"points\": 25", "\"points\": 101")))
                .isInstanceOf(InvalidRingException.class)
                .hasMessageContaining("ring at position 4")
                .hasMessageContaining("101");
    }
}
