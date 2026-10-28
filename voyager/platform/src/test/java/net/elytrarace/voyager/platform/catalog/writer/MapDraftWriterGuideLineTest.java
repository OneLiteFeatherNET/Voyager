package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuideLineMode;
import net.elytrarace.voyager.api.race.GuideParticle;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.DraftFixtures;
import net.elytrarace.voyager.platform.catalog.adapter.MapDraftAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapDraftWriterGuideLineTest {

    /** A loaded map that names DUST and a colour keeps both through a save, as palm_beach does. */
    @Test
    void savingALoadedMapKeepsItsParticleAndItsDustColour() {
        MapDraft base = DraftFixtures.complete(new MapId("palmbeach"));
        GuideLine violet = new GuideLine(base.guideLine().points(), base.guideLine().lookAheadRings(),
                base.guideLine().particleSpacing(), GuideLineMode.FULL, GuideParticle.DUST, 0x7A1FB8);
        MapDraft draft = new MapDraft(base.id(), base.world(), base.spawn(), base.rings(),
                base.referenceTimeSeconds(), new BoostConfig(30, 40, 12, 3.5), violet,
                new RingEffectTuning(2.0, 0.25));

        MapDraft loaded = MapDraftAdapter.read("palmbeach.json",
                JsonParser.parseString(MapDraftJsonWriter.toJson(draft)));
        JsonObject saved = JsonParser.parseString(MapDraftJsonWriter.toJson(loaded)).getAsJsonObject();

        JsonObject line = saved.getAsJsonObject("guideLine");
        assertThat(line.get("particle").getAsString()).as("particle after a save").isEqualTo("DUST");
        assertThat(line.get("dustColor").getAsString()).as("dustColor after a save").isEqualTo("#7A1FB8");
    }
}
