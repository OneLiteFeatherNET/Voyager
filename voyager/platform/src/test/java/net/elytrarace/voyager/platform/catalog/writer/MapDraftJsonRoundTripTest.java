package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.DraftFixtures;
import net.elytrarace.voyager.platform.catalog.adapter.MapDefinitionAdapter;
import net.elytrarace.voyager.platform.catalog.adapter.Vec3Adapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** A complete draft, written by the setup server, must be read without error by the game server's map adapter. */
class MapDraftJsonRoundTripTest {

    private static final Gson GAME_GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3.class, new Vec3Adapter())
            .registerTypeAdapter(MapDefinition.class, new MapDefinitionAdapter())
            .create();

    @Test
    void aCompleteDraftIsReadBackByTheGameAdapterFieldForFieldAndRingForRing() {
        var draft = DraftFixtures.complete(new MapId("skyfortress"));

        MapDefinition map = GAME_GSON.fromJson(MapDraftJsonWriter.toJson(draft), MapDefinition.class);

        assertThat(map.name()).isEqualTo("skyfortress");
        assertThat(map.world()).isEqualTo("skyfortress");
        assertThat(map.spawn()).isEqualTo(draft.spawn());
        assertThat(map.rings()).isEqualTo(draft.rings());
        assertThat(map.referenceTime()).isEqualTo(Duration.ofSeconds(60));
        assertThat(map.boostConfig()).isEqualTo(draft.boostConfig());
        assertThat(map.guideLine()).isEqualTo(draft.guideLine());
    }
}
