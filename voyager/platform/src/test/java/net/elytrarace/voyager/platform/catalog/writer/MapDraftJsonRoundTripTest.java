package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.elytrarace.voyager.api.math.Vec3;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideLineMode;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.DraftFixtures;
import net.elytrarace.voyager.platform.catalog.adapter.MapDefinitionAdapter;
import net.elytrarace.voyager.platform.catalog.adapter.MapDraftAdapter;
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

    /**
     * Setup must not drop a designer's tuning on a save: the optional fields come back out of the file the
     * setup server wrote, through the draft reader and through the game reader, unchanged.
     */
    @Test
    void aDraftKeepsTheNewFieldsThroughASaveAndALoad() {
        MapDraft base = DraftFixtures.complete(new MapId("skyfortress"));
        MapDraft draft = new MapDraft(base.id(), base.world(), base.spawn(), base.rings(),
                base.referenceTimeSeconds(), new BoostConfig(30, 40, 12, 3.5), base.guideLine(),
                new RingEffectTuning(2.0, 0.25));
        String saved = MapDraftJsonWriter.toJson(draft);

        MapDraft reloaded = MapDraftAdapter.read("skyfortress.json", JsonParser.parseString(saved));
        MapDefinition map = GAME_GSON.fromJson(saved, MapDefinition.class);

        assertThat(reloaded.boostConfig()).isEqualTo(new BoostConfig(30, 40, 12, 3.5));
        assertThat(reloaded.ringEffects()).isEqualTo(new RingEffectTuning(2.0, 0.25));
        assertThat(map.boostConfig()).isEqualTo(new BoostConfig(30, 40, 12, 3.5));
        assertThat(map.ringEffects()).isEqualTo(new RingEffectTuning(2.0, 0.25));
    }

    @Test
    void aDraftKeepsTheGuideLineStyleThroughALoadAndASave() {
        MapDraft base = DraftFixtures.complete(new MapId("skyfortress"));
        GuideLine styled = new GuideLine(base.guideLine().points(), base.guideLine().lookAheadRings(),
                base.guideLine().particleSpacing(), GuideLineMode.WINDOW, GuideParticle.DUST, 0x3366FF);
        MapDraft draft = new MapDraft(base.id(), base.world(), base.spawn(), base.rings(),
                base.referenceTimeSeconds(), new BoostConfig(30, 40, 12, 3.5), styled,
                new RingEffectTuning(2.0, 0.25));
        String saved = MapDraftJsonWriter.toJson(draft);

        MapDraft reloaded = MapDraftAdapter.read("skyfortress.json", JsonParser.parseString(saved));
        MapDefinition map = GAME_GSON.fromJson(saved, MapDefinition.class);

        assertThat(reloaded.guideLine()).isEqualTo(styled);
        assertThat(map.guideLine()).isEqualTo(styled);
    }
}
