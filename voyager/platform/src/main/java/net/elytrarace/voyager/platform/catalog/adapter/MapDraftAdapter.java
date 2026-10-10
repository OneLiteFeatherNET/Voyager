package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.mapsetup.MapDraft;
import net.elytrarace.voyager.api.mapsetup.MapId;
import net.elytrarace.voyager.api.race.Ring;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a draft file. It accepts what the game's map adapter refuses, because a draft is allowed to be incomplete: a
 * missing {@code spawn} and an empty {@code rings} array are the draft's problems, not the file's. Every other field
 * is read exactly as the game's adapter reads it, through the same helpers.
 */
@ApiStatus.Internal
public final class MapDraftAdapter {

    private MapDraftAdapter() {
    }

    /**
     * @param fileName the file's name, which a read failure names before the field
     * @param element  the file's top-level JSON
     * @return the draft
     * @throws JsonParseException if the file is malformed; the message names the file and the field
     */
    public static MapDraft read(String fileName, JsonElement element) {
        try {
            return readDraft(element);
        } catch (JsonParseException exception) {
            throw new JsonParseException("%s: %s".formatted(fileName, exception.getMessage()), exception);
        }
    }

    private static MapDraft readDraft(JsonElement element) {
        JsonObject json = JsonFields.object(element, "a draft");
        String name = JsonFields.string(json, "name", "a draft");
        String what = "draft '%s'".formatted(name);
        SchemaVersion.read(json, what);

        String world = JsonFields.present(json, "world") ? JsonFields.string(json, "world", what) : name;
        @Nullable Vec3 spawn = JsonFields.present(json, "spawn") ? Vec3Adapter.read(json.get("spawn")) : null;

        JsonArray ringElements = JsonFields.array(json, "rings", what);
        List<Ring> rings = new ArrayList<>(ringElements.size());
        for (int position = 0; position < ringElements.size(); position++) {
            rings.add(RingAdapter.read(ringElements.get(position), position));
        }

        return new MapDraft(
                new MapId(name),
                world,
                spawn,
                rings,
                JsonFields.number(json, "referenceTimeSeconds", what),
                MapDefinitionAdapter.boostConfig(json, what),
                MapDefinitionAdapter.guideLine(json, what));
    }
}
