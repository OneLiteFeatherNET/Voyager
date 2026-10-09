package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;
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
 * <p>{@code boostConfig} is read as two tick counts rather than the milliseconds the old format used
 * for its cooldown. The server counts a burn in ticks and nothing else, so a millisecond value here
 * would be converted on every load and would let a file ask for a cooldown of 2010 ms that no server
 * can honour — the rounding would be invisible in the file and visible in the race. The conversion
 * happened once, in {@code tools/map-converter}, and the committed file carries the answer.
 *
 * <p>A {@code notes} array is expected in these files and deliberately not read. The values the old
 * data never carried — the spawn, the reference time, a ring's score and how far ahead the racing
 * line reaches — were seeded during conversion, and the notes are how a file says so to the person
 * editing it. Unknown fields in
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
                Duration.ofMillis(Math.round(seconds * 1000.0)),
                boostConfig(json, what),
                guideLine(json, what, context));
    }

    /**
     * Reads the racing line: the control points that bend it, and the two numbers that decide how it
     * is drawn.
     *
     * <p>Required, like the boost tuning above it and for the same reason — but note what is
     * <em>not</em> being defaulted here. A course with no guide points at all is perfectly normal and
     * says so with an empty {@code points} array; what has no harmless default is
     * {@code lookAheadRings}, which decides whether the racer is shown the next stretch or the whole
     * 1588-block course at once. A file that omitted the block and silently got somebody's idea of a
     * sensible look-ahead would be a course tuned by a constant nobody can find from the data.
     */
    private static GuideLine guideLine(JsonObject json, String what, JsonDeserializationContext context) {
        JsonObject line = JsonFields.object(
                JsonFields.required(json, "guideLine", what), "%s field 'guideLine'".formatted(what));
        String where = "%s guide line".formatted(what);

        List<GuidePoint> points = new ArrayList<>();
        for (JsonElement point : JsonFields.array(line, "points", where)) {
            JsonObject guide = JsonFields.object(point, "%s guide point".formatted(what));
            int orderIndex = JsonFields.integer(guide, "orderIndex", where);
            points.add(new GuidePoint(orderIndex,
                    context.deserialize(JsonFields.required(guide, "position",
                            "%s guide point %s".formatted(what, orderIndex)), Vec3.class)));
        }
        return new GuideLine(points,
                JsonFields.integer(line, "lookAheadRings", where),
                JsonFields.number(line, "particleSpacing", where));
    }

    /**
     * Reads the per-map boost tuning, required like every other field here.
     *
     * <p>Required rather than defaulted for the reason {@code JsonFields} exists at all: an absent
     * boost configuration has no harmless value. Falling back to some built-in default would let a
     * map file that forgot the block, or misspelt it, race with tuning nobody chose and nobody can
     * find in the data — and the design's whole claim about configuration is that there is one type
     * and the value lives in the file.
     */
    private static BoostConfig boostConfig(JsonObject json, String what) {
        JsonObject boost = JsonFields.object(
                JsonFields.required(json, "boostConfig", what), "%s field 'boostConfig'".formatted(what));
        String where = "%s boost config".formatted(what);
        return new BoostConfig(
                JsonFields.integer(boost, "burnDurationTicks", where),
                JsonFields.integer(boost, "cooldownTicks", where));
    }
}
