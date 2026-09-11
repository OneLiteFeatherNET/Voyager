package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one racecourse file.
 *
 * <p>The reference time is stored in seconds and may be fractional, because it is a lap time a
 * designer edits by hand and {@code 46.7} is what a lap time looks like. It is converted to a
 * {@link Duration} at millisecond resolution — one server tick is fifty of those, so nothing that
 * matters is lost, and {@link MapDefinition} refuses a value that rounds to zero.
 *
 * <p>A {@code notes} array is expected in these files and deliberately not read. The three values
 * the old data never carried — the spawn, the reference time and a ring's score — were seeded during
 * conversion, and the notes are how a file says so to the person editing it. Unknown fields in
 * general are ignored rather than rejected: every field this record needs is required by name above,
 * so a misspelt one already fails as an absent one, and rejecting the rest would make a comment an
 * error.
 */
@ApiStatus.Internal
public final class MapDefinitionAdapter implements JsonDeserializer<MapDefinition> {

    @Override
    public MapDefinition deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
        JsonObject json = JsonFields.object(element, "a map");
        String name = JsonFields.string(json, "name", "a map");
        String what = "map '%s'".formatted(name);

        List<Ring> rings = new ArrayList<>();
        for (JsonElement ring : JsonFields.array(json, "rings", what)) {
            rings.add(context.deserialize(ring, Ring.class));
        }

        double seconds = JsonFields.number(json, "referenceTimeSeconds", what);
        if (!Double.isFinite(seconds)) {
            throw new JsonParseException("%s has a reference time of %s".formatted(what, seconds));
        }

        return new MapDefinition(
                name,
                JsonFields.string(json, "world", what),
                context.deserialize(JsonFields.required(json, "spawn", what), Vec3.class),
                rings,
                Duration.ofMillis(Math.round(seconds * 1000.0)));
    }
}
