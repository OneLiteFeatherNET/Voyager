package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideLineMode;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.api.race.effect.exception.InvalidRingEffectException;
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

    /** The fixture's boost block with the optional fields added, so each test states only what it changes. */
    private static String withBoost(String boostFields) {
        return MAP.replace("\"boostConfig\": { \"burnDurationTicks\": 18, \"cooldownTicks\": 41 }",
                "\"boostConfig\": { \"burnDurationTicks\": 18, \"cooldownTicks\": 41" + boostFields + " }");
    }

    /** A file that states none of the new fields loads with the values it always had. */
    @Test
    void aFileWithoutTheNewFieldsLoadsWithTodaysValues() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.boostConfig().rocketsPerRacer()).isEqualTo(64);
        assertThat(map.boostConfig().maxSpeedBlocksPerTick()).isNull();
        assertThat(map.ringEffects()).isEqualTo(RingEffectTuning.DEFAULT);
    }

    @Test
    void rocketsPerRacerIsReadFromTheBoostConfig() {
        MapDefinition map = Adapters.GSON.fromJson(withBoost(", \"rocketsPerRacer\": 12"), MapDefinition.class);

        assertThat(map.boostConfig().rocketsPerRacer()).isEqualTo(12);
    }

    @Test
    void rocketsPerRacerOf65IsRefusedAndTheMessageNamesTheField() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withBoost(", \"rocketsPerRacer\": 65"), MapDefinition.class))
                .isInstanceOf(InvalidBoostConfigException.class)
                .hasMessageContaining("rocketsPerRacer");
    }

    @Test
    void theSpeedCapIsReadWhenTheMapStatesOne() {
        MapDefinition map = Adapters.GSON.fromJson(withBoost(", \"maxSpeedBlocksPerTick\": 3.5"), MapDefinition.class);

        assertThat(map.boostConfig().maxSpeedBlocksPerTick()).isEqualTo(3.5);
    }

    @Test
    void aFileWithRingEffectsLoadsItsFactors() {
        String withEffects = MAP.replace("\"guideLine\"",
                "\"ringEffects\": { \"boostMultiplier\": 2.0, \"slowMultiplier\": 0.25 },\n  \"guideLine\"");

        MapDefinition map = Adapters.GSON.fromJson(withEffects, MapDefinition.class);

        assertThat(map.ringEffects()).isEqualTo(new RingEffectTuning(2.0, 0.25));
    }

    /** A factor a map leaves out keeps its own default, so a file may state only the one it changes. */
    @Test
    void aFactorLeftOutOfRingEffectsKeepsItsDefault() {
        String onlyBoost = MAP.replace("\"guideLine\"",
                "\"ringEffects\": { \"boostMultiplier\": 2.0 },\n  \"guideLine\"");

        MapDefinition map = Adapters.GSON.fromJson(onlyBoost, MapDefinition.class);

        assertThat(map.ringEffects()).isEqualTo(new RingEffectTuning(2.0, RingEffectTuning.DEFAULT.slowMultiplier()));
    }

    @Test
    void aNegativeRingEffectFactorIsRefusedAndTheMessageNamesTheField() {
        String negative = MAP.replace("\"guideLine\"",
                "\"ringEffects\": { \"boostMultiplier\": -1 },\n  \"guideLine\"");

        assertThatThrownBy(() -> Adapters.GSON.fromJson(negative, MapDefinition.class))
                .isInstanceOf(InvalidRingEffectException.class)
                .hasMessageContaining("boostMultiplier");
    }

    @Test
    void readsTheNameWorldSpawnAndReferenceTime() {
        MapDefinition map = Adapters.GSON.fromJson(MAP, MapDefinition.class);

        assertThat(map.key().asString()).isEqualTo("elytrarace:elytraraceblueandred");
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

    @Test
    void aGuideLineWithNoStyleLoadsAsFullEndRodAndAmber() {
        GuideLine line = Adapters.GSON.fromJson(MAP, MapDefinition.class).guideLine();

        assertThat(line.mode()).isEqualTo(GuideLineMode.FULL);
        assertThat(line.particle()).isEqualTo(GuideParticle.END_ROD);
        assertThat(line.dustRgbOrDefault()).isEqualTo(0xFFC800);
    }

    @Test
    void aGuideLineWithTheThreeFieldsLoadsThem() {
        String styled = MAP.replace("\"particleSpacing\": 1.5,",
                "\"particleSpacing\": 1.5, \"mode\": \"WINDOW\", \"particle\": \"DUST\", \"dustColor\": \"#3366FF\",");

        GuideLine line = Adapters.GSON.fromJson(styled, MapDefinition.class).guideLine();

        assertThat(line.mode()).isEqualTo(GuideLineMode.WINDOW);
        assertThat(line.particle()).isEqualTo(GuideParticle.DUST);
        assertThat(line.dustRgbOrDefault()).isEqualTo(0x3366FF);
    }

    @Test
    void anUnknownModeIsRefusedAndTheMessageNamesTheField() {
        String unknown = MAP.replace("\"particleSpacing\": 1.5,",
                "\"particleSpacing\": 1.5, \"mode\": \"SPIRAL\",");

        assertThatThrownBy(() -> Adapters.GSON.fromJson(unknown, MapDefinition.class))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("guideLine.mode")
                .hasMessageContaining("SPIRAL");
    }

    @Test
    void aColourOnEndRodIsRefused() {
        String colourOnEndRod = MAP.replace("\"particleSpacing\": 1.5,",
                "\"particleSpacing\": 1.5, \"particle\": \"END_ROD\", \"dustColor\": \"#3366FF\",");

        assertThatThrownBy(() -> Adapters.GSON.fromJson(colourOnEndRod, MapDefinition.class))
                .isInstanceOf(InvalidGuideLineException.class)
                .hasMessageContaining("dustColor");
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
        assertThat(Adapters.GSON.fromJson(MAP, MapDefinition.class).key().asString()).isEqualTo("elytrarace:elytraraceblueandred");
        assertThat(Adapters.GSON.fromJson(MAP.replace("\"notes\"", "\"somethingElseEntirely\""),
                MapDefinition.class).key().asString()).isEqualTo("elytrarace:elytraraceblueandred");
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
        assertThat(Adapters.GSON.fromJson(MAP, MapDefinition.class).key().asString()).isEqualTo("elytrarace:elytraraceblueandred");
    }

    @Test
    void readsSchemaVersionOne() {
        assertThat(Adapters.GSON.fromJson(withSchemaVersion("1"), MapDefinition.class).key().asString())
                .isEqualTo("elytrarace:elytraraceblueandred");
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
        assertThatThrownBy(() -> Adapters.GSON.fromJson(withSchemaVersion("4"), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("map 'elytraraceblueandred' declares schemaVersion 4")
                .hasMessageContaining("up to schemaVersion 3");
    }

    @Test
    void aVersionTwoMapWithoutWorldTakesTheValueOfItsKeyAsItsWorld() {
        MapDefinition map = Adapters.GSON.fromJson(versionTwo("\"key\": \"elytrarace:sky_drift\","), MapDefinition.class);

        assertThat(map.key().asString()).isEqualTo("elytrarace:sky_drift");
        assertThat(map.world()).isEqualTo("sky_drift");
    }

    @Test
    void aVersionTwoMapKeepsAnExplicitWorldWhenItStatesOne() {
        MapDefinition map = Adapters.GSON.fromJson(versionTwo("\"key\": \"elytrarace:blue_and_red\", \"world\": "
                + "\"ElytraraceBlueAndRed\","), MapDefinition.class);

        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
    }

    @Test
    void aVersionTwoMapWithAKeyInAnotherNamespaceIsRefusedNamingTheKey() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(versionTwo("\"key\": \"other:blue_and_red\","),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'key'");
    }

    @Test
    void aVersionTwoMapThatStatesANameIsRefusedNamingTheName() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(versionTwo("\"key\": \"elytrarace:blue_and_red\", "
                + "\"name\": \"blue_and_red\","), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'name'");
    }

    @Test
    void aVersionOneFlatNameWithCapitalsIsRefusedNamingTheName() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replace("elytraraceblueandred", "ElytraraceBlueAndRed"),
                MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'name'");
    }

    @Test
    void theAuthorsOfAMapAreReadWithTheirUuidInLowerCase() {
        MapDefinition map = Adapters.GSON.fromJson(MAP.replaceFirst("\\{", "{\n  \"authors\": [ { \"uuid\": "
                + "\"05BF52C6-7BB0-4F13-8951-0E1FD803DF35\", \"name\": \"TheMeinerLP\" } ],"), MapDefinition.class);

        assertThat(map.authors()).singleElement()
                .satisfies(author -> assertThat(author.uuid().toString())
                        .isEqualTo("05bf52c6-7bb0-4f13-8951-0e1fd803df35"));
    }

    @Test
    void aMapWithoutAuthorsIsNotRefused() {
        assertThat(Adapters.GSON.fromJson(MAP, MapDefinition.class).authors()).isEmpty();
    }

    @Test
    void anAuthorWithAUuidThatIsNotInTheCanonicalFormIsRefusedNamingTheField() {
        assertThatThrownBy(() -> Adapters.GSON.fromJson(MAP.replaceFirst("\\{", "{\n  \"authors\": [ { \"uuid\": "
                + "\"05bf52c6-7bb0-4f13-8951-0e1fd803df3\", \"name\": \"TheMeinerLP\" } ],"), MapDefinition.class))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("field 'uuid'");
    }

    private static String versionTwo(String identity) {
        return MAP.replaceFirst("\"name\": \"elytraraceblueandred\",", "\"schemaVersion\": 2, " + identity)
                .replaceFirst("\"world\": \"ElytraraceBlueAndRed\",", "");
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
