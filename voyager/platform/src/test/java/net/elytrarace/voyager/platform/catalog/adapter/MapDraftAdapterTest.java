package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapDraftAdapterTest {

    private static final String SKELETON = """
            {"schemaVersion": 1, "name": "skyfortress", "referenceTimeSeconds": 60.0,
             "boostConfig": {"burnDurationTicks": 30, "cooldownTicks": 40},
             "guideLine": {"points": [], "lookAheadRings": 2, "particleSpacing": 1.0},
             "rings": []}
            """;

    @Test
    void aDraftWithoutSpawnAndWithoutRingsIsAccepted() {
        MapDraft draft = MapDraftAdapter.read("skyfortress.json", JsonParser.parseString(SKELETON));

        assertThat(draft.id()).isEqualTo(new MapId("skyfortress"));
        assertThat(draft.world()).isEqualTo("skyfortress");
        assertThat(draft.spawn()).isNull();
        assertThat(draft.rings()).isEmpty();
    }

    @Test
    void aMissingRingFieldIsReportedWithTheFileNameAndTheFieldName() {
        String broken = SKELETON.replace("\"rings\": []",
                "\"rings\": [{\"center\": {\"x\": 0, \"y\": 0, \"z\": 0}, \"normal\": {\"x\": 1, \"y\": 0, \"z\": 0},"
                        + " \"radius\": 2.0, \"points\": 10}]");

        assertThatThrownBy(() -> MapDraftAdapter.read("skyfortress.json", JsonParser.parseString(broken)))
                .isInstanceOf(JsonParseException.class)
                .hasMessageContaining("skyfortress.json")
                .hasMessageContaining("'type'");
    }
}
