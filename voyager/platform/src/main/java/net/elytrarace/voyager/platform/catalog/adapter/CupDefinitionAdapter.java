package net.elytrarace.voyager.platform.catalog.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one cup file: a name, a mode, and the maps of its rotation in order.
 *
 * <p>The order of {@code mapNames} is the rotation, so the list is built in file order and never
 * sorted. The names are not resolved here — a cup that names an unknown map is a cross-catalogue
 * question, answered once by {@code CatalogConsistency} where both directories are in hand.
 */
@ApiStatus.Internal
public final class CupDefinitionAdapter implements JsonDeserializer<CupDefinition> {

    @Override
    public CupDefinition deserialize(JsonElement element, Type type, JsonDeserializationContext context) {
        JsonObject json = JsonFields.object(element, "a cup");
        String name = JsonFields.string(json, "name", "a cup");
        String what = "cup '%s'".formatted(name);
        SchemaVersion.read(json, what);

        List<String> mapNames = new ArrayList<>();
        for (JsonElement mapName : JsonFields.array(json, "mapNames", what)) {
            if (!mapName.isJsonPrimitive() || !mapName.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("%s lists the map %s, which is not a name".formatted(what, mapName));
            }
            mapNames.add(mapName.getAsString());
        }

        String modeName = JsonFields.string(json, "mode", what);
        return new CupDefinition(name, mapNames, GameMode.byName(modeName).orElseThrow(
                () -> new JsonParseException("%s has the unrecognised mode '%s'".formatted(what, modeName))));
    }
}
