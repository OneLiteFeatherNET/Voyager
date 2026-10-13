package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.stream.JsonWriter;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

/**
 * Writes a draft in the key order of the shipped map: {@code schemaVersion} first, then {@code name}, {@code world},
 * {@code spawn} (left out while no spawn is set), {@code referenceTimeSeconds}, {@code boostConfig}, {@code guideLine}
 * and {@code rings}. Every ring writes {@code index}, always.
 */
@ApiStatus.Internal
public abstract class MapDraftJsonWriter {

    /** The schema version this writer produces; the adapter of the game server reads version 1. */
    static final int SCHEMA_VERSION = 1;

    private MapDraftJsonWriter() {
    }

    /**
     * @param draft the draft to write
     * @return the draft as indented JSON text
     */
    @Contract(pure = true, value = "_ -> new")
    public static String toJson(MapDraft draft) {
        StringWriter text = new StringWriter();
        try (JsonWriter json = new JsonWriter(text)) {
            json.setIndent("  ");
            writeDraft(json, draft);
        } catch (IOException exception) {
            // A StringWriter does not fail; the checked exception is the JsonWriter API's, not the medium's.
            throw new UncheckedIOException(exception);
        }
        return text.toString();
    }

    private static void writeDraft(JsonWriter json, MapDraft draft) throws IOException {
        json.beginObject();
        json.name("schemaVersion").value(SCHEMA_VERSION);
        json.name("name").value(draft.id().value());
        json.name("world").value(draft.world());
        if (draft.spawn() != null) {
            json.name("spawn");
            vector(json, draft.spawn());
        }
        json.name("referenceTimeSeconds").value(draft.referenceTimeSeconds());
        boostConfig(json, draft.boostConfig());
        ringEffects(json, draft.ringEffects());
        guideLine(json, draft.guideLine());
        json.name("rings").beginArray();
        for (Ring ring : draft.rings()) {
            ring(json, ring);
        }
        json.endArray();
        json.endObject();
    }

    /**
     * Writes the two required boost fields, and each optional one only when it differs from what a file that omits it
     * means: 64 rockets, and no speed cap. A saved file therefore changes only where a designer set a value.
     */
    private static void boostConfig(JsonWriter json, BoostConfig boost) throws IOException {
        json.name("boostConfig").beginObject()
                .name("burnDurationTicks").value(boost.burnDurationTicks())
                .name("cooldownTicks").value(boost.cooldownTicks());
        if (boost.rocketsPerRacer() != BoostConfig.MAX_ROCKETS_PER_RACER) {
            json.name("rocketsPerRacer").value(boost.rocketsPerRacer());
        }
        if (boost.maxSpeedBlocksPerTick() != null) {
            json.name("maxSpeedBlocksPerTick").value(boost.maxSpeedBlocksPerTick());
        }
        json.endObject();
    }

    /** Writes {@code ringEffects} only when the map sets a factor away from its default, and then only that factor. */
    private static void ringEffects(JsonWriter json, RingEffectTuning tuning) throws IOException {
        if (tuning.equals(RingEffectTuning.DEFAULT)) {
            return;
        }
        json.name("ringEffects").beginObject();
        if (tuning.boostMultiplier() != RingEffectTuning.DEFAULT.boostMultiplier()) {
            json.name("boostMultiplier").value(tuning.boostMultiplier());
        }
        if (tuning.slowMultiplier() != RingEffectTuning.DEFAULT.slowMultiplier()) {
            json.name("slowMultiplier").value(tuning.slowMultiplier());
        }
        json.endObject();
    }

    private static void guideLine(JsonWriter json, GuideLine line) throws IOException {
        json.name("guideLine").beginObject();
        json.name("points").beginArray();
        for (GuidePoint point : line.points()) {
            json.beginObject().name("orderIndex").value(point.orderIndex());
            json.name("position");
            vector(json, point.position());
            json.endObject();
        }
        json.endArray();
        json.name("lookAheadRings").value(line.lookAheadRings());
        json.name("particleSpacing").value(line.particleSpacing());
        json.name("mode").value(line.mode().name());
        json.name("particle").value(line.particle().name());
        if (line.dustRgb() != null) {
            json.name("dustColor").value("#%06X".formatted(line.dustRgb()));
        }
        json.endObject();
    }

    private static void ring(JsonWriter json, Ring ring) throws IOException {
        json.beginObject();
        json.name("index").value(ring.index());
        json.name("center");
        vector(json, ring.center());
        json.name("normal");
        vector(json, ring.normal());
        json.name("radius").value(ring.radius());
        json.name("points").value(ring.points());
        json.name("type").value(ring.type().name());
        json.endObject();
    }

    private static void vector(JsonWriter json, Vec3 vector) throws IOException {
        json.beginObject()
                .name("x").value(vector.x())
                .name("y").value(vector.y())
                .name("z").value(vector.z())
                .endObject();
    }
}
