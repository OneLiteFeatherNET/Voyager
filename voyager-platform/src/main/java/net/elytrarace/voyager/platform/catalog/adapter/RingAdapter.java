package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Type;

/**
 * Reads one ring: its index, centre, normal, radius, score and type.
 *
 * <p>The normal is read as stored rather than re-derived — that is decision D-E3-5, and the
 * conversion that produced these files is where the derivation happened, once, with the flight path
 * in hand to settle the sign. {@link Ring} still checks that what arrives is unit length, so a file
 * edited by hand into a normal of length 1.4 is refused here rather than quietly scoring rings at an
 * angle.
 *
 * <p>An unrecognised type is refused rather than defaulted to {@code STANDARD}. The old loader
 * defaulted, which turns a misspelt {@code BOOST} into a ring that scores but does not boost — a
 * course that plays wrong while every file in it looks right.
 */
@ApiStatus.Internal
public final class RingAdapter implements JsonDeserializer<Ring> {

    @Override
    public Ring deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
        JsonObject json = JsonFields.object(element, "a ring");
        int index = JsonFields.integer(json, "index", "a ring");
        String what = "ring %s".formatted(index);
        String typeName = JsonFields.string(json, "type", what);

        return new Ring(
                index,
                context.deserialize(JsonFields.required(json, "center", what), Vec3.class),
                context.deserialize(JsonFields.required(json, "normal", what), Vec3.class),
                JsonFields.number(json, "radius", what),
                JsonFields.integer(json, "points", what),
                RingType.byName(typeName).orElseThrow(() -> new JsonParseException(
                        "%s has the unrecognised type '%s'".formatted(what, typeName))));
    }
}
