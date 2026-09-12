package net.elytrarace.tools.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;

import java.util.List;

/**
 * Renders a converted map or cup as the JSON the catalogue in {@code voyager-platform} reads back.
 *
 * <p>The format is written here and parsed there, by hand on both sides, so the two can drift. What
 * stops them is not discipline but a test: {@code CommittedMapDataTest} loads the committed
 * {@code ElytraraceBlueAndRed} through the real catalogue and checks the values this writer put in
 * it. If the shapes disagree, that test is what says so.
 *
 * <p><strong>Why the notes are in the file.</strong> Four of the values a {@code MapDefinition}
 * needs do not exist in every old file — the spawn, the reference time, a ring's score and the burn
 * half of the boost tuning — so each was seeded where it was missing. The design spec classes all of
 * them as versioned JSON owned by the game designer, which
 * means a balancing pass has to be a data change rather than a code change; a seed that is not
 * marked as one becomes a measurement the first time somebody reads the file without this context.
 * The notes are for the reader and are ignored on load.
 */
public final class CatalogWriter {

    private CatalogWriter() {
    }

    /**
     * @param map   the converted racecourse
     * @param notes provenance for the values the old data did not carry
     * @return the object to write to {@code maps/<name>.json}
     */
    public static JsonObject toJson(MapDefinition map, List<String> notes) {
        JsonObject json = new JsonObject();
        json.addProperty("name", map.name());
        json.addProperty("world", map.world());
        json.add("spawn", vector(map.spawn()));
        // Seconds rather than an ISO-8601 duration string: this number is meant to be edited by a
        // designer reading the notes below it, and "60.0" invites that where "PT1M" does not.
        json.addProperty("referenceTimeSeconds", map.referenceTime().toMillis() / 1000.0);
        json.add("boostConfig", boostConfig(map.boostConfig()));

        JsonArray rings = new JsonArray();
        for (Ring ring : map.rings()) {
            rings.add(ring(ring));
        }
        json.add("rings", rings);
        json.add("notes", notes(notes));
        return json;
    }

    /**
     * @param cup   the converted rotation
     * @param notes provenance for the values the old data did not carry
     * @return the object to write to {@code cups/<name>.json}
     */
    public static JsonObject toJson(CupDefinition cup, List<String> notes) {
        JsonObject json = new JsonObject();
        json.addProperty("name", cup.name());
        json.addProperty("mode", cup.mode().name());

        JsonArray mapNames = new JsonArray();
        cup.mapNames().forEach(mapNames::add);
        json.add("mapNames", mapNames);
        json.add("notes", notes(notes));
        return json;
    }

    /**
     * Both numbers in ticks, unlike the old format's {@code cooldownMs}.
     *
     * <p>The conversion from milliseconds is done once, here, rather than on every load: a server
     * counts a burn in ticks and in nothing else, so a file carrying 2010 ms would be asking for
     * something no server can do, and the rounding that made it 40 ticks anyway would be invisible in
     * the file and visible in the race.
     */
    private static JsonObject boostConfig(BoostConfig boost) {
        JsonObject json = new JsonObject();
        json.addProperty("burnDurationTicks", boost.burnDurationTicks());
        json.addProperty("cooldownTicks", boost.cooldownTicks());
        return json;
    }

    private static JsonObject ring(Ring ring) {
        JsonObject json = new JsonObject();
        json.addProperty("index", ring.index());
        json.add("center", vector(ring.center()));
        json.add("normal", vector(ring.normal()));
        json.addProperty("radius", ring.radius());
        json.addProperty("points", ring.points());
        json.addProperty("type", ring.type().name());
        return json;
    }

    private static JsonObject vector(Vec3 vector) {
        JsonObject json = new JsonObject();
        json.addProperty("x", vector.x());
        json.addProperty("y", vector.y());
        json.addProperty("z", vector.z());
        return json;
    }

    private static JsonArray notes(List<String> notes) {
        JsonArray array = new JsonArray();
        notes.forEach(array::add);
        return array;
    }
}
