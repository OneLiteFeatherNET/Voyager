package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.DraftFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapDraftJsonWriterTest {

    private static final MapId ID = new MapId("skyfortress");

    @Test
    void theKeysAreWrittenInTheOrderOfTheShippedMapWithSchemaVersionFirst() {
        JsonObject json = parse(MapDraftJsonWriter.toJson(DraftFixtures.complete(ID)));

        assertThat(json.keySet()).containsExactly(
                "schemaVersion", "name", "world", "spawn", "referenceTimeSeconds", "boostConfig", "guideLine", "rings");
        assertThat(json.get("schemaVersion").getAsInt()).isEqualTo(1);
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

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
