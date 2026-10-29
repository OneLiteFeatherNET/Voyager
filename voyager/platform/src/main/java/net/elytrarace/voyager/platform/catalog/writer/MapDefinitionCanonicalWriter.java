package net.elytrarace.voyager.platform.catalog.writer;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingShape;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * Writes the layout geometry of a map definition as canonical JSON: the spawn and the rings, one line with no
 * whitespace, lists in list order and every double through one formatter. The same geometry therefore always has the
 * same bytes, which is what {@link LayoutVersion} hashes.
 *
 * <p>Only the geometry is written. {@code name} is already the {@code map_key}, {@code world} is a deployment detail,
 * {@code referenceTime} and {@code guideLine} are presentation, and {@code boostConfig} and {@code ringEffects} are
 * tuning. A tuning revision must not reset the leaderboards of a layout (design D7), so none of them is written.
 *
 * <p>Nothing is read from a {@code Map}, so no iteration order can leak into the text. The writer is written by hand
 * rather than with a reflective serializer, so that a new geometric component is a visible change here and never a
 * silent one.
 */
@ApiStatus.Internal
public abstract class MapDefinitionCanonicalWriter {

    private MapDefinitionCanonicalWriter() {
    }

    /**
     * @param map the definition whose geometry is written
     * @return the canonical form of the spawn and rings of {@code map}
     * @throws IllegalArgumentException if a component is a double that is not finite, which JSON cannot carry
     */
    @Contract(pure = true, value = "_ -> new")
    public static String toCanonicalJson(MapDefinition map) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        key(json, "spawn");
        vector(json, map.spawn());
        json.append(',');
        key(json, "rings");
        rings(json, map.rings());
        return json.append('}').toString();
    }

    private static void rings(StringBuilder json, List<Ring> rings) {
        json.append('[');
        for (int index = 0; index < rings.size(); index++) {
            Ring ring = rings.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append('{');
            key(json, "index").append(ring.index()).append(',');
            key(json, "center");
            vector(json, ring.center());
            json.append(',');
            key(json, "normal");
            vector(json, ring.normal());
            json.append(',');
            key(json, "radius").append(number(ring.radius())).append(',');
            key(json, "points").append(ring.points()).append(',');
            text(json, "type", ring.type().name());
            if (ring.shape() instanceof RingShape.Ellipse oval) {
                json.append(',');
                key(json, "radiusMinor").append(number(oval.radiusMinor())).append(',');
                key(json, "majorAxis");
                vector(json, oval.majorAxis());
            }
            json.append('}');
        }
        json.append(']');
    }

    private static void vector(StringBuilder json, Vec3 vector) {
        json.append('{');
        key(json, "x").append(number(vector.x())).append(',');
        key(json, "y").append(number(vector.y())).append(',');
        key(json, "z").append(number(vector.z()));
        json.append('}');
    }

    /** Every double of the definition goes through here, so one value has one spelling. */
    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("a canonical number must be finite, was %s".formatted(value));
        }
        return Double.toString(value);
    }

    private static StringBuilder text(StringBuilder json, String key, String value) {
        key(json, key);
        return json.append(quoted(value));
    }

    /** Writes {@code "key":} and returns the builder, ready for the value. */
    private static StringBuilder key(StringBuilder json, String key) {
        return json.append(quoted(key)).append(':');
    }

    private static String quoted(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"' || character == '\\') {
                quoted.append('\\').append(character);
            } else if (character < ' ') {
                quoted.append("\\u%04x".formatted((int) character));
            } else {
                quoted.append(character);
            }
        }
        return quoted.append('"').toString();
    }
}
