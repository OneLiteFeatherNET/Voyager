package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.elytrarace.voyager.api.math.Vec3;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Type;

/**
 * Reads {@code {"x": …, "y": …, "z": …}} into a {@link Vec3}.
 *
 * <p>All three components are required. A vector with a defaulted component is the failure this
 * whole adapter layer exists to make impossible: it is a valid vector, at a plausible place, and
 * nothing downstream can tell it from one somebody meant.
 */
@ApiStatus.Internal
public final class Vec3Adapter implements JsonDeserializer<Vec3> {

    @Override
    public Vec3 deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
        JsonObject json = JsonFields.object(element, "a vector");
        return new Vec3(
                JsonFields.number(json, "x", "a vector"),
                JsonFields.number(json, "y", "a vector"),
                JsonFields.number(json, "z", "a vector"));
    }
}
