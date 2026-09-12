package net.elytrarace.tools.converter;

import com.google.gson.JsonObject;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogWriterTest {

    private static final MapDefinition MAP = new MapDefinition(
            "elytraraceblueandred",
            "ElytraraceBlueAndRed",
            new Vec3(109, -62, 54),
            List.of(
                    new Ring(0, new Vec3(85, -54, 54), new Vec3(-1, 0, 0), Math.sqrt(13), 10, RingType.STANDARD),
                    new Ring(1, new Vec3(2, -31, 69), new Vec3(-2.0 / 3, 2.0 / 3, -1.0 / 3), 3, 25,
                            RingType.BOOST)),
            Duration.ofMillis(46_700),
            new BoostConfig(18, 41));

    @Test
    void writesEveryFieldAMapDefinitionIsRebuiltFrom() {
        JsonObject json = CatalogWriter.toJson(MAP, List.of("a note"));

        assertThat(json.get("name").getAsString()).isEqualTo("elytraraceblueandred");
        assertThat(json.get("world").getAsString()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(json.getAsJsonObject("spawn").get("x").getAsDouble()).isEqualTo(109.0);
        assertThat(json.getAsJsonObject("spawn").get("y").getAsDouble()).isEqualTo(-62.0);
        assertThat(json.getAsJsonObject("spawn").get("z").getAsDouble()).isEqualTo(54.0);
        // Seconds, fractional: a reference time of 46.7 s must not be rounded to 46 on the way out.
        assertThat(json.get("referenceTimeSeconds").getAsDouble()).isEqualTo(46.7);
        assertThat(json.getAsJsonArray("notes")).singleElement()
                .satisfies(note -> assertThat(note.getAsString()).isEqualTo("a note"));
    }

    /**
     * Both boost numbers, in ticks and under their own object. 18 and 41 are distinct and not
     * multiples of one another, so a writer that emitted one field twice shows here.
     */
    @Test
    void writesTheBoostTuningAsTwoTickCounts() {
        JsonObject json = CatalogWriter.toJson(MAP, List.of());

        JsonObject boost = json.getAsJsonObject("boostConfig");
        assertThat(boost.get("burnDurationTicks").getAsInt()).isEqualTo(18);
        assertThat(boost.get("cooldownTicks").getAsInt()).isEqualTo(41);
        assertThat(boost.has("cooldownMs"))
                .describedAs("milliseconds were the old format; a server counts in ticks")
                .isFalse();
    }

    @Test
    void writesARadiusAtFullPrecisionSoTheHitAreaSurvivesTheRoundTrip() {
        JsonObject json = CatalogWriter.toJson(MAP, List.of());

        // sqrt(13) is the radius of all 35 rings of the shipped course. Written short it would come
        // back a different ring, and every ring on the map would be a slightly different size than
        // the one the builder drew.
        assertThat(json.getAsJsonArray("rings").get(0).getAsJsonObject().get("radius").getAsDouble())
                .isEqualTo(Math.sqrt(13));
    }

    @Test
    void writesEachRingsOwnIndexPointsTypeAndVectors() {
        JsonObject json = CatalogWriter.toJson(MAP, List.of());

        JsonObject second = json.getAsJsonArray("rings").get(1).getAsJsonObject();
        assertThat(second.get("index").getAsInt()).isEqualTo(1);
        assertThat(second.get("points").getAsInt()).isEqualTo(25);
        assertThat(second.get("type").getAsString()).isEqualTo("BOOST");
        assertThat(second.getAsJsonObject("center").get("y").getAsDouble()).isEqualTo(-31.0);
        // A tilted normal has no zero component, so a writer that mixed up two axes shows here.
        assertThat(second.getAsJsonObject("normal").get("x").getAsDouble()).isEqualTo(-2.0 / 3);
        assertThat(second.getAsJsonObject("normal").get("y").getAsDouble()).isEqualTo(2.0 / 3);
        assertThat(second.getAsJsonObject("normal").get("z").getAsDouble()).isEqualTo(-1.0 / 3);
    }

    @Test
    void writesACupsNameModeAndRotationInOrder() {
        CupDefinition cup = new CupDefinition(
                "test_cup", List.of("nether-sprint", "elytraraceblueandred"), GameMode.PRACTICE);

        JsonObject json = CatalogWriter.toJson(cup, List.of("first", "second"));

        assertThat(json.get("name").getAsString()).isEqualTo("test_cup");
        assertThat(json.get("mode").getAsString()).isEqualTo("PRACTICE");
        assertThat(json.getAsJsonArray("mapNames")).extracting(element -> element.getAsString())
                .containsExactly("nether-sprint", "elytraraceblueandred");
        assertThat(json.getAsJsonArray("notes")).extracting(element -> element.getAsString())
                .containsExactly("first", "second");
    }
}
