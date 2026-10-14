package net.elytrarace.voyager.platform.catalog.writer;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

import java.util.List;

/**
 * Writes a map definition as canonical JSON: one line with no whitespace, every component in one fixed order, lists in
 * list order, durations as whole milliseconds and every double through one formatter. The same definition therefore
 * always has the same bytes, which is what {@link LayoutVersion} hashes.
 *
 * <p>Nothing is read from a {@code Map}, so no iteration order can leak into the text. The writer is written by hand
 * rather than with a reflective serializer, so that a new component of the definition is a visible change here and
 * never a silent one.
 */
@ApiStatus.Internal
public abstract class MapDefinitionCanonicalWriter {

    private MapDefinitionCanonicalWriter() {
    }

    /**
     * @param map the definition to write
     * @return the canonical form of {@code map}
     * @throws IllegalArgumentException if a component is a double that is not finite, which JSON cannot carry
     */
    @Contract(pure = true, value = "_ -> new")
    public static String toCanonicalJson(MapDefinition map) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        text(json, "name", map.name()).append(',');
        text(json, "world", map.world()).append(',');
        key(json, "spawn");
        vector(json, map.spawn());
        json.append(',');
        key(json, "rings");
        rings(json, map.rings());
        json.append(',');
        key(json, "referenceTimeMillis").append(map.referenceTime().toMillis()).append(',');
        key(json, "boostConfig");
        boostConfig(json, map.boostConfig());
        json.append(',');
        key(json, "guideLine");
        guideLine(json, map.guideLine());
        json.append(',');
        key(json, "ringEffects");
        ringEffects(json, map.ringEffects());
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
            json.append('}');
        }
        json.append(']');
    }

    private static void boostConfig(StringBuilder json, BoostConfig boost) {
        json.append('{');
        key(json, "burnDurationTicks").append(boost.burnDurationTicks()).append(',');
        key(json, "cooldownTicks").append(boost.cooldownTicks()).append(',');
        key(json, "rocketsPerRacer").append(boost.rocketsPerRacer()).append(',');
        key(json, "maxSpeedBlocksPerTick");
        json.append(boost.maxSpeedBlocksPerTick() == null ? "null" : number(boost.maxSpeedBlocksPerTick()));
        json.append('}');
    }

    private static void guideLine(StringBuilder json, GuideLine line) {
        json.append('{');
        key(json, "points").append('[');
        List<GuidePoint> points = line.points();
        for (int index = 0; index < points.size(); index++) {
            GuidePoint point = points.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append('{');
            key(json, "orderIndex").append(point.orderIndex()).append(',');
            key(json, "position");
            vector(json, point.position());
            json.append('}');
        }
        json.append("],");
        key(json, "lookAheadRings").append(line.lookAheadRings()).append(',');
        key(json, "particleSpacing").append(number(line.particleSpacing())).append(',');
        text(json, "mode", line.mode().name()).append(',');
        text(json, "particle", line.particle().name()).append(',');
        key(json, "dustRgb");
        json.append(line.dustRgb() == null ? "null" : line.dustRgb().toString());
        json.append('}');
    }

    private static void ringEffects(StringBuilder json, RingEffectTuning tuning) {
        json.append('{');
        key(json, "boostMultiplier").append(number(tuning.boostMultiplier())).append(',');
        key(json, "slowMultiplier").append(number(tuning.slowMultiplier()));
        json.append('}');
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
