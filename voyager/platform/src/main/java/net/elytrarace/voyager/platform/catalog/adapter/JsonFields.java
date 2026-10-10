package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Reads fields out of a {@link JsonObject}, refusing an absent or wrongly typed one by name.
 *
 * <p>Gson's reflective binding leaves an absent field at its type's default, and for the numbers a
 * racecourse is made of that default is a perfectly plausible value: a ring whose {@code radius} was
 * never written becomes a ring of radius zero, and one missing its {@code y} moves to the height a
 * lot of maps genuinely use. Every field here is therefore required and every failure names the
 * field, which is why the adapters are hand-written rather than bound reflectively.
 */
final class JsonFields {

    private JsonFields() {
    }

    static JsonObject object(JsonElement element, String what) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("%s must be an object, was %s".formatted(what, element));
        }
        return element.getAsJsonObject();
    }

    static JsonElement required(JsonObject json, String field, String what) {
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull()) {
            throw new JsonParseException("%s is missing the field '%s'".formatted(what, field));
        }
        return value;
    }

    /**
     * Whether the field is stated at all. A field stated as {@code null} counts as absent, the same way
     * {@link #required} treats it, so a file cannot opt out of a default by writing {@code null}.
     */
    static boolean present(JsonObject json, String field) {
        JsonElement value = json.get(field);
        return value != null && !value.isJsonNull();
    }

    static String string(JsonObject json, String field, String what) {
        JsonElement value = required(json, field, what);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("%s field '%s' must be a string, was %s".formatted(what, field, value));
        }
        return value.getAsString();
    }

    static double number(JsonObject json, String field, String what) {
        JsonElement value = required(json, field, what);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("%s field '%s' must be a number, was %s".formatted(what, field, value));
        }
        return value.getAsDouble();
    }

    static int integer(JsonObject json, String field, String what) {
        double value = number(json, field, what);
        if (value != Math.rint(value)) {
            throw new JsonParseException(
                    "%s field '%s' must be a whole number, was %s".formatted(what, field, value));
        }
        return (int) value;
    }

    static JsonArray array(JsonObject json, String field, String what) {
        JsonElement value = required(json, field, what);
        if (!value.isJsonArray()) {
            throw new JsonParseException("%s field '%s' must be an array, was %s".formatted(what, field, value));
        }
        return value.getAsJsonArray();
    }
}
