package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.api.race.CatalogKeys;
import net.elytrarace.voyager.api.race.exception.InvalidAuthorException;
import net.elytrarace.voyager.api.race.exception.InvalidCatalogKeyException;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads the identity of a map, cup or draft file: its key, and its authors. Shared by the three adapters so that the
 * rule for each schema version, and its messages, cannot diverge between the kinds of file (ADR-0040).
 *
 * <p>A version 1 file states a flat {@code name}, which must match the key's value rule; its key is
 * {@code elytrarace:<name>}. A version 2 file states a {@code key} in full and no {@code name}. Every refusal names the
 * field, so the message says which of the two the file got wrong.
 */
final class CatalogIdentity {

    private static final String NAME = "name";
    private static final String KEY = "key";
    private static final String AUTHORS = "authors";
    private static final Pattern UUID_FORM = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private CatalogIdentity() {
    }

    /**
     * The description of a file for messages: {@code "<kind> '<name or key>'"} when the file states either, else
     * {@code "a <kind>"}. Used before the version is known, so it reads whichever identifier the file has.
     */
    static String describe(JsonObject json, String kind) {
        for (String field : new String[] {NAME, KEY}) {
            JsonElement value = json.get(field);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                return "%s '%s'".formatted(kind, value.getAsString());
            }
        }
        return "a %s".formatted(kind);
    }

    /**
     * The key of a file of the given schema version.
     *
     * @param json    the file's top-level object
     * @param version the file's schema version, as {@link SchemaVersion#read} returned it
     * @param what    the file's description for messages
     * @return the key, in the {@code elytrarace} namespace
     * @throws JsonParseException if the identifier is absent, in the wrong field for the version, or not a valid key
     */
    static Key read(JsonObject json, int version, String what) {
        if (version >= 2) {
            if (JsonFields.present(json, NAME)) {
                throw new JsonParseException(
                        "%s field 'name' is not used in schemaVersion %s; state the identifier in 'key'"
                                .formatted(what, version));
            }
            return keyOf(JsonFields.string(json, KEY, what), what);
        }
        if (JsonFields.present(json, KEY)) {
            throw new JsonParseException("%s field 'key' needs schemaVersion 2, and this file is schemaVersion %s"
                    .formatted(what, version));
        }
        return flatNameOf(JsonFields.string(json, NAME, what), what);
    }

    /** The key of a version 1 flat name: {@code elytrarace:<name>}, refused if the name does not match the value rule. */
    private static Key flatNameOf(String name, String what) {
        try {
            return CatalogKeys.of(name);
        } catch (InvalidCatalogKeyException exception) {
            throw new JsonParseException("%s field 'name': %s".formatted(what, exception.getMessage()));
        }
    }

    private static Key keyOf(String text, String what) {
        Key key;
        try {
            key = Key.key(text);
        } catch (InvalidKeyException exception) {
            throw new JsonParseException("%s field 'key' is not a key, was '%s'".formatted(what, text));
        }
        try {
            return CatalogKeys.checked(key);
        } catch (InvalidCatalogKeyException exception) {
            throw new JsonParseException("%s field 'key': %s".formatted(what, exception.getMessage()));
        }
    }

    /**
     * The optional authors of a map, cup or draft: an array of objects with a {@code uuid} and a {@code name}. A file
     * that states none has none.
     */
    static List<Author> authors(JsonObject json, String what) {
        if (!JsonFields.present(json, AUTHORS)) {
            return List.of();
        }
        JsonArray elements = JsonFields.array(json, AUTHORS, what);
        List<Author> authors = new ArrayList<>(elements.size());
        for (JsonElement element : elements) {
            JsonObject author = JsonFields.object(element, "%s author".formatted(what));
            String where = "%s author".formatted(what);
            String uuidText = JsonFields.string(author, "uuid", where);
            if (!UUID_FORM.matcher(uuidText).matches()) {
                throw new JsonParseException(
                        "%s field 'uuid' must be a UUID in the form 8-4-4-4-12 hexadecimal digits, was '%s'"
                                .formatted(where, uuidText));
            }
            String name = JsonFields.string(author, "name", where);
            try {
                authors.add(new Author(UUID.fromString(uuidText), name));
            } catch (InvalidAuthorException exception) {
                throw new JsonParseException("%s field 'name': %s".formatted(where, exception.getMessage()));
            }
        }
        try {
            return Author.checked(authors);
        } catch (InvalidAuthorException exception) {
            throw new JsonParseException("%s field 'authors': %s".formatted(what, exception.getMessage()));
        }
    }
}
