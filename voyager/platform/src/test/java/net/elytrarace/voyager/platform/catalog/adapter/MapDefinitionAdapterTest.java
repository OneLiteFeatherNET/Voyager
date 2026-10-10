package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.exception.InvalidBoostConfigException;
import net.elytrarace.voyager.api.race.exception.InvalidGuideLineException;
import net.elytrarace.voyager.api.race.exception.InvalidMapException;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapDefinitionAdapterTest {

    // The name and the world differ, so a reader that used one for the other cannot pass; the spawn
    // is nowhere near either ring centre; the reference time is fractional.
    private static final String MAP = """
            {
              "name": "elytraraceblueandred",
              "world": "ElytraraceBlueAndRed",
              "spawn": { "x": 109.0, "y": -62.0, "z": 54.0 },
              "referenceTimeSeconds": 46.7,
              "boostConfig": { "burnDurationTicks": 18, "cooldownTicks": 41 },
              "rings": [
                {
                  "index": 0,
                  "center": { "x": 85.0, "y": -54.0, "z": 54.0 },
                  "normal": { "x": -1.0, "y": 0.0, "z": 0.0 },
                  "radius": 3.605551275463989,
                  "points": 10,
                  "type": "STANDARD"
                },
                {
                  "index": 1,
                  "center": { "x": 2.0, "y": -31.0, "z": 69.0 },
                  "normal": { "x": 0.0, "y": 1.0, "z": 0.0 },
                  "radius": 3.0,
                  "points": 25,
                  "type": "BOOST"
                }
              ],
              "guideLine": {
                "lookAheadRings": 4,
                "particleSpacing": 1.5,
                "points": [
                  { "orderIndex": 75, "position": { "x": 41.0, "y": -42.0, "z": 63.0 } },
                  { "orderIndex": 25, "position": { "x": 63.0, "y": -48.0, "z": 58.0 } }
                ]
              },
              "notes": [ "PROVISIONAL seed", "another line" ]
            }
            """;

    @Test
    void readsTheNameWorldSpawnAndReferenceTime() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.name()).isEqualTo("elytraraceblueandred");
        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(map.spawn()).isEqualTo(new Vec3(109, -62, 54));
        // 46.7 s, not 46 and not 47: the value is a lap time a designer edits, and rounding it to
        // whole seconds would move every medal bracket on the map.
        assertThat(map.referenceTime()).isEqualTo(Duration.ofMillis(46_700));
    }

    /**
     * Both numbers, in ticks. 18 and 41 are distinct, not multiples of one another, and neither is
     * the Vanilla-derived default of 30 — so an adapter that read one field into the other, or that
     * fell back to a built-in, produces a value that is nowhere in this fixture.
     */
    @Test
    void readsTheBoostTuningAsTwoTickCounts() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.boostConfig()).isEqualTo(new BoostConfig(18, 41));
    }

    @Test
    void refusesAMapMissingItsBoostTuning() {
        // Not defaulted, for the reason every other field here is not: an absent boost configuration
        // has no harmless value, and a map racing on tuning nobody chose is not findable in the data.
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"boostConfig\".*\n", "\n"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'boostConfig'");
    }

    @Test
    void refusesABoostConfigMissingOneOfItsTwoNumbers() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"burnDurationTicks\": 18, ", ""), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'burnDurationTicks'");
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace(", \"cooldownTicks\": 41", ""), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'cooldownTicks'");
    }

    /**
     * The one invariant that is not about this file being well formed: a cooldown no longer than the
     * burn lets two rockets burn on one racer at once, which the simulation's boolean input cannot
     * express. {@code BoostConfig} refuses it and the refusal has to survive the read rather than
     * being swallowed into some default.
     */
    @Test
    void letsTheBoostConfigItselfRefuseACooldownThatDoesNotOutlastTheBurn() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"cooldownTicks\": 41", "\"cooldownTicks\": 18"), MapDefinition.class))
                .isInstanceOf(InvalidBoostConfigException.class)
                .hasMessageContaining("cannot express");
    }

    /**
     * Both numbers and both points, and the points sorted into the order the line reaches them
     * regardless of the order the file lists them in.
     *
     * <p>The fixture lists 75 before 25 on purpose. Nothing else in this file can tell a reader that
     * preserved file order from one that ordered by order index, and the difference is a line that
     * doubles back between two guides in the same gap. 4 and 1.5 are likewise neither of each other's
     * values nor any default in the repository.
     */
    @Test
    void readsTheGuideLineAndOrdersItsPointsByOrderIndex() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.guideLine().lookAheadRings()).isEqualTo(4);
        assertThat(map.guideLine().particleSpacing()).isEqualTo(1.5);
        assertThat(map.guideLine().points()).containsExactly(
                new GuidePoint(25, new Vec3(63, -48, 58)),
                new GuidePoint(75, new Vec3(41, -42, 63)));
    }

    @Test
    void readsACourseWithNoGuidePointsAtAll() {
        // Legal, and the shape three of the four maps in the repository have: the line is then the
        // rings alone. The empty array is still required — "this course needs no guides" is a thing
        // the file says, not a thing a missing block implies.
        MapDefinition map = Adapters.GSON.fromJson(withNoGuidePoints(MAP), MapDefinition.class);

        assertThat(map.guideLine().points()).isEmpty();
        assertThat(map.guideLine().lookAheadRings()).isEqualTo(4);
    }

    @Test
    void refusesAMapMissingItsGuideLine() {
        // Not defaulted, for the same reason the boost tuning is not. lookAheadRings decides whether a
        // racer is shown the next stretch or the whole course at once, and a built-in fallback would
        // be a course tuned by a number that is nowhere in the data.
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withNoGuideLine(MAP), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'guideLine'");
    }

    /** The guide points replaced by an empty array. The ring field also called "points" is left alone. */
    private static String withNoGuidePoints(String map) {
        return map.replaceAll("(?s)\"points\": \\[\\s*\\{.*?\\]", "\"points\": []");
    }

    /** The whole {@code guideLine} block removed, from its key up to the {@code notes} that follow it. */
    private static String withNoGuideLine(String map) {
        return map.substring(0, map.indexOf("  \"guideLine\"")) + map.substring(map.indexOf("  \"notes\""));
    }

    @Test
    void refusesAGuideLineMissingItsLookAhead() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"lookAheadRings\": 4,", ""), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'lookAheadRings'");
    }

    /**
     * A guide point on a ring's own slot has no defined position in the line, and the record refuses
     * it. The refusal has to survive the read rather than being swallowed into a default, exactly as
     * the boost config's does.
     */
    @Test
    void letsTheGuidePointItselfRefuseAnOrderIndexOnARingsSlot() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"orderIndex\": 75", "\"orderIndex\": 100"), MapDefinition.class))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("ring 1's own slot");
    }

    @Test
    void readsTheRingsInFileOrder() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.rings()).extracting(Ring::index).containsExactly(0, 1);
        assertThat(map.rings()).extracting(Ring::points).containsExactly(10, 25);
        assertThat(map.rings().get(0).center()).isEqualTo(new Vec3(85, -54, 54));
        assertThat(map.rings().get(1).center()).isEqualTo(new Vec3(2, -31, 69));
    }

    @Test
    void ignoresTheNotesTheConversionLeftForTheReader() {
        // The notes say which values were seeded rather than measured. They are for a person, and a
        // reader that choked on them would make an explanation an error.
        assertThat(Adapters.GSON.fromJson(MAP, MapDefinition.class).name()).isEqualTo("elytraraceblueandred");
        assertThat(Adapters.GSON.fromJson(MAP.replace("\"notes\"", "\"somethingElseEntirely\""),
                MapDefinition.class).name()).isEqualTo("elytraraceblueandred");
    }

    @Test
    void refusesAMapMissingItsSpawn() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"spawn\".*\n", "\n"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'spawn'");
    }

    @Test
    void refusesAMapMissingItsReferenceTime() {
        // Not defaulted. The reference time decides the medal tiers, and a map that silently used
        // somebody's idea of a sensible default would hand out the wrong medals quietly.
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"referenceTimeSeconds\".*\n", "\n"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("missing the field 'referenceTimeSeconds'");
    }

    @Test
    void readsARingWithoutIndexAsItsPositionInTheRingsArray() {
        MapDefinition map = Adapters.GSON.fromJson(MAP.replace("\"index\": 1,", ""), MapDefinition.class);

        assertThat(map.rings()).extracting(Ring::index).containsExactly(0, 1);
    }

    @Test
    void refusesARingWhoseDeclaredIndexIsNotItsPosition() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replace("\"index\": 1,", "\"index\": 9,"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("ring at position 1 declares index 9");
    }

    @Test
    void readsTheSameMapWithOrWithoutItsRingIndexes() {
        MapDefinition declared = Adapters.GSON.fromJson(MAP, MapDefinition.class);
        MapDefinition derived = Adapters.GSON.fromJson(
                MAP.replaceAll("\\s*\"index\": \\d+,", ""), MapDefinition.class);

        assertThat(derived).isEqualTo(declared);
    }

    @Test
    void readsAMapWithoutWorldAsItsNameExactlyAsWritten() {
        // The name is lower case and the shipped world is not; a derivation that case-folded the
        // name would pass the shipped file's own test and fail this one.
        MapDefinition map = Adapters.GSON.fromJson(MAP.replaceAll("\\s*\"world\".*\n", "\n"), MapDefinition.class);

        assertThat(map.world()).isEqualTo("elytraraceblueandred");
    }

    @Test
    void keepsAnExplicitWorldUnchangedWhenItDiffersFromTheName() {
        MapDefinition map = Adapters.GSON.fromJson(
                MAP.replace("\"world\": \"ElytraraceBlueAndRed\"", "\"world\": \"Sky Drift Dir\""),
                MapDefinition.class);

        assertThat(map.world()).isEqualTo("Sky Drift Dir");
    }

    @Test
    void refusesAPresentButBlankWorldNamingTheField() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"world\": \"ElytraraceBlueAndRed\"", "\"world\": \"  \""), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'world' must not be blank");
    }

    @Test
    void readsAMapWithoutSchemaVersionAsVersionOne() {
        assertThat(Adapters.GSON.fromJson(MAP, MapDefinition.class).name()).isEqualTo("elytraraceblueandred");
    }

    @Test
    void readsSchemaVersionOne() {
        assertThat(Adapters.GSON.fromJson(withSchemaVersion("1"), MapDefinition.class).name())
                .isEqualTo("elytraraceblueandred");
    }

    @Test
    void refusesSchemaVersionZero() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withSchemaVersion("0"), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'schemaVersion' must be a whole number of 1 or more");
    }

    @Test
    void refusesFractionalSchemaVersion() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withSchemaVersion("1.5"), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'schemaVersion' must be a whole number of 1 or more");
    }

    @Test
    void refusesTextSchemaVersion() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withSchemaVersion("\"1\""), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'schemaVersion' must be a whole number of 1 or more");
    }

    @Test
    void refusesSchemaVersionAboveTheSupportedMaximumNamingBothNumbers() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withSchemaVersion("2"), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("map 'elytraraceblueandred' declares schemaVersion 2")
                .hasMessageContaining("up to schemaVersion 1");
    }

    private static String withSchemaVersion(String value) {
        return MAP.replaceFirst("\\{", "{\n  \"schemaVersion\": " + value + ",");
    }

    @Test
    void letsTheMapItselfRefuseAReferenceTimeOfZero() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(
                MAP.replace("\"referenceTimeSeconds\": 46.7", "\"referenceTimeSeconds\": 0"),
                MapDefinition.class))
                .isInstanceOf(InvalidMapException.class);
    }
}
