package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Reads the {@code schemaVersion} of a map or cup file, shared by both adapters so that the rule and its
 * messages cannot diverge between the two kinds of file.
 *
 * <p>An absent field is version 1, the format every file so far was written in. A version this server
 * reads is accepted; a version above {@link #CURRENT} is refused with the supported maximum named, so an
 * older server reports a newer file plainly rather than reading it under the wrong rules.
 */
final class SchemaVersion {

    /** The highest schema version this server reads. Raised only with a migration for the old version. */
    static final int CURRENT = 1;

    private static final String FIELD = "schemaVersion";

    private SchemaVersion() {
    }

    /**
     * @param json the file's top-level object
     * @param what the file's description for messages, such as {@code "map 'sky-drift'"}
     * @return the declared version, or 1 when the field is absent
     * @throws JsonParseException if the version is not a whole number of at least 1, or is above {@link #CURRENT}
     */
    static int read(JsonObject json, String what) {
        if (!JsonFields.present(json, FIELD)) {
            return 1;
        }
        JsonElement value = json.get(FIELD);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw notWholeNumber(what, value);
        }
        double declared = value.getAsDouble();
        if (declared != Math.rint(declared) || declared < 1) {
            throw notWholeNumber(what, value);
        }
        if (declared > CURRENT) {
            throw new JsonParseException(
                    "%s declares schemaVersion %s, but this server reads up to schemaVersion %s"
                            .formatted(what, value, CURRENT));
        }
        return (int) declared;
    }

    private static JsonParseException notWholeNumber(String what, JsonElement value) {
        return new JsonParseException(
                "%s field '%s' must be a whole number of 1 or more, was %s".formatted(what, FIELD, value));
    }
}
