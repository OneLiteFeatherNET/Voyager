package net.elytrarace.voyager.platform.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.elytrarace.voyager.api.race.CatalogKeys;
import net.elytrarace.voyager.api.race.CupDefinition;

import net.kyori.adventure.key.Key;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The boot check for display names: one line for the played cup and for each of its maps whose file gives no
 * {@code displayName}, so an operator sees which entries are shown in title case rather than by a name they wrote.
 *
 * <p>Only the played cup and its maps are reported. An unplayed map without a name is not played, so nobody reads it.
 */
@ApiStatus.Internal
public final class UnnamedEntries {

    private static final String MAPS = "maps";
    private static final String CUPS = "cups";

    private UnnamedEntries() {
    }

    /**
     * The warnings for the played cup and its maps, in play order, the cup last.
     *
     * @param dataDirectory the directory holding {@code maps/} and {@code cups/}
     * @param played        the cup the server plays
     * @return one line per unnamed entry; empty when every one has a name
     */
    public static List<String> warnings(Path dataDirectory, CupDefinition played) {
        Set<String> unnamedMaps = unnamedKeys(dataDirectory.resolve(MAPS));
        Set<String> unnamedCups = unnamedKeys(dataDirectory.resolve(CUPS));
        List<String> warnings = new ArrayList<>();
        for (String mapName : played.mapNames()) {
            if (unnamedMaps.contains(mapName)) {
                warnings.add(warning("map", mapName));
            }
        }
        String cupName = played.key().asString();
        if (unnamedCups.contains(cupName)) {
            warnings.add(warning("cup", cupName));
        }
        return List.copyOf(warnings);
    }

    private static String warning(String kind, String key) {
        return ("%s '%s' has no displayName in its file, so it is shown as '%s'; "
                + "write a displayName to name it").formatted(kind, key, CatalogKeys.titleCase(Key.key(key)));
    }

    /** The keys of the files in {@code directory} that give no display name, blank counting as none. */
    private static Set<String> unnamedKeys(Path directory) {
        Set<String> keys = new HashSet<>();
        if (!Files.isDirectory(directory)) {
            return keys;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().endsWith(".json")).forEach(file -> {
                JsonObject json = read(file);
                if (json != null && !named(json)) {
                    keys.add(keyOf(json));
                }
            });
        } catch (IOException unreadable) {
            return keys;
        }
        return keys;
    }

    private static boolean named(JsonObject json) {
        if (!json.has("displayName") || !json.get("displayName").isJsonPrimitive()) {
            return false;
        }
        return !json.get("displayName").getAsString().isBlank();
    }

    /** The key a file names: its {@code key} when stated, otherwise its flat {@code name} read as {@code elytrarace:<name>}. */
    private static String keyOf(JsonObject json) {
        JsonElement key = json.has("key") ? json.get("key") : json.get("name");
        return CatalogKeys.normalise(key.getAsString());
    }

    private static JsonObject read(Path file) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (IOException | JsonParseException unreadable) {
            return null;
        }
    }
}
