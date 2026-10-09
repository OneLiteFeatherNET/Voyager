package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.mapsetup.MapId;
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

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
