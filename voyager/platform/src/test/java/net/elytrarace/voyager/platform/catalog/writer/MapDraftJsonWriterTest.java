package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingShape;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.DraftFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapDraftJsonWriterTest {

    private static final MapId ID = new MapId("skyfortress");

    @Test
    void theKeysAreWrittenInTheOrderOfTheShippedMapWithSchemaVersionFirst() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(DraftFixtures.complete(ID)));

        assertThat(json.keySet()).containsExactly(
                "schemaVersion", "key", "world", "spawn", "referenceTimeSeconds", "boostConfig", "guideLine", "rings");
        assertThat(json.get("schemaVersion").getAsInt()).isEqualTo(2);
        assertThat(json.get("key").getAsString()).isEqualTo("elytrarace:skyfortress");
    }

    @Test
    void theDisplayNameAndTheAuthorsAreWrittenAfterTheWorldWhenSet() {
        MapDraft base = DraftFixtures.complete(ID);
        MapDraft named = new MapDraft(base.id(), base.world(), base.spawn(), base.rings(), base.referenceTimeSeconds(),
                base.boostConfig(), base.guideLine(), base.ringEffects(), "Sky Fortress",
                List.of(new Author(UUID.fromString("05bf52c6-7bb0-4f13-8951-0e1fd803df35"), "TheMeinerLP")));

        JsonObject json = parse(MapDraftJsonWriter.toJson(named));

        assertThat(json.keySet()).containsSubsequence("key", "world", "displayName", "authors", "spawn");
        assertThat(json.get("displayName").getAsString()).isEqualTo("Sky Fortress");
        JsonObject author = json.getAsJsonArray("authors").get(0).getAsJsonObject();
        assertThat(author.get("uuid").getAsString()).isEqualTo("05bf52c6-7bb0-4f13-8951-0e1fd803df35");
        assertThat(author.get("name").getAsString()).isEqualTo("TheMeinerLP");
    }

    @Test
    void aDraftWithoutADisplayNameOrAuthorsWritesNeitherKey() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(DraftFixtures.complete(ID)));

        assertThat(json.has("displayName")).isFalse();
        assertThat(json.has("authors")).isFalse();
    }

    /** A draft that states none of the optional fields writes exactly the file it wrote before they existed. */
    @Test
    void aDefaultRocketCountNoCapAndRingEffectsAreNotWritten() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(DraftFixtures.complete(ID)));

        assertThat(json.getAsJsonObject("boostConfig").keySet())
                .containsExactly("burnDurationTicks", "cooldownTicks");
        assertThat(json.has("ringEffects")).isFalse();
    }

    @Test
    void theOptionalFieldsAreWrittenWhenTheyDifferFromTheirDefaults() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(
                tuned(new BoostConfig(30, 40, 12, 3.5), new RingEffectTuning(2.0, 0.5))));

        JsonObject boost = json.getAsJsonObject("boostConfig");
        assertThat(boost.get("rocketsPerRacer").getAsInt()).isEqualTo(12);
        assertThat(boost.get("maxSpeedBlocksPerTick").getAsDouble()).isEqualTo(3.5);
        JsonObject effects = json.getAsJsonObject("ringEffects");
        assertThat(effects.get("boostMultiplier").getAsDouble()).isEqualTo(2.0);
        assertThat(effects.has("slowMultiplier"))
                .describedAs("slowMultiplier is at its default and is not written")
                .isFalse();
    }

    @Test
    void theSpawnKeyIsOmittedWhileNoSpawnIsSet() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(DraftFixtures.skeleton(ID)));

        assertThat(json.has("spawn")).isFalse();
        assertThat(json.getAsJsonArray("rings")).isEmpty();
    }

    @Test
    void everyRingWritesItsIndexAndItsTypeAsTheNameOfTheEnumConstant() {
        JsonArray rings = parse(MapDraftJsonWriter.toJson(DraftFixtures.complete(ID))).getAsJsonArray("rings");

        JsonObject second = rings.get(1).getAsJsonObject();
        assertThat(second.keySet()).containsExactly("index", "center", "normal", "radius", "points", "type");
        assertThat(second.get("index").getAsInt()).isEqualTo(1);
        assertThat(second.get("type").getAsString()).isEqualTo("BOOST");
    }

    /** The complete draft with its boost tuning and ring factors replaced; everything else is the fixture's. */
    static MapDraft tuned(BoostConfig boost, RingEffectTuning effects) {
        MapDraft base = DraftFixtures.complete(ID);
        return new MapDraft(base.id(), base.world(), base.spawn(), base.rings(), base.referenceTimeSeconds(),
                boost, base.guideLine(), effects);
    }

    @Test
    void aDraftWithAnOvalIsWrittenInVersionThreeWithTheOvalFieldsAfterTheType() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(withRings(oval())));

        assertThat(json.get("schemaVersion").getAsInt()).isEqualTo(3);
        JsonObject ring = json.getAsJsonArray("rings").get(0).getAsJsonObject();
        assertThat(ring.keySet()).containsExactly(
                "index", "center", "normal", "radius", "points", "type", "radiusMinor", "majorAxis");
        assertThat(ring.get("radius").getAsDouble()).isEqualTo(6.0);
        assertThat(ring.get("radiusMinor").getAsDouble()).isEqualTo(3.0);
        assertThat(ring.getAsJsonObject("majorAxis").get("x").getAsDouble()).isEqualTo(1.0);
    }

    @Test
    void aDraftOfCirclesIsWrittenInVersionTwoWithNoOvalField() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(DraftFixtures.complete(ID)));

        assertThat(json.get("schemaVersion").getAsInt()).isEqualTo(2);
        JsonObject ring = json.getAsJsonArray("rings").get(0).getAsJsonObject();
        assertThat(ring.has("radiusMinor")).isFalse();
        assertThat(ring.has("majorAxis")).isFalse();
    }

    private static Ring oval() {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        return new Ring(0, new Vec3(10.0, 70.0, -20.0), up,
                RingShape.of(6.0, 3.0, new Vec3(1.0, 0.0, 0.0), up), 10, RingType.STANDARD);
    }

    /** The complete draft with its rings replaced; everything else is the fixture's. */
    private static MapDraft withRings(Ring ring) {
        MapDraft base = DraftFixtures.complete(ID);
        return new MapDraft(base.id(), base.world(), base.spawn(), List.of(ring), base.referenceTimeSeconds(),
                base.boostConfig(), base.guideLine(), base.ringEffects());
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
