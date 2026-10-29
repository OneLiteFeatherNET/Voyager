package net.elytrarace.voyager.platform.catalog.writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.adapter.MapDefinitionAdapter;
import net.elytrarace.voyager.platform.catalog.adapter.Vec3Adapter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization of the circle path: the layout version of every shipped map, read from the committed files.
 *
 * <p>A layout version is the identity of a course, and a leaderboard is keyed by it. A change to the ring model must
 * leave the layout version of every map that has only circles exactly as it is, so these values are pinned as they
 * are on {@code main}. The test reads the committed files from the module-relative path of the server's resources,
 * the same deliberate exception {@code FileSchemaTest} makes, because the test is about the data that ships.
 */
class ShippedLayoutVersionTest {

    private static final Path MAPS = Path.of("..", "server", "src", "main", "resources", "maps");

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3.class, new Vec3Adapter())
            .registerTypeAdapter(MapDefinition.class, new MapDefinitionAdapter())
            .create();

    @Test
    void everyShippedMapKeepsTheLayoutVersionItHasOnMain() throws IOException {
        assertThat(layoutVersionsByFile())
                .as("layout versions of the shipped maps, keyed by file name")
                .isEqualTo(Map.of(
                        "blue_and_red.json", "77ece620b237b510",
                        "dragon_lobby.json", "e27f6f3861138e5b",
                        "memoria.json", "54c1cc5f6cfc7e20",
                        "palm_beach.json", "db67c48047ea0c0f"));
    }

    private static Map<String, String> layoutVersionsByFile() throws IOException {
        Map<String, String> versions = new TreeMap<>();
        try (var files = Files.list(MAPS)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                MapDefinition map = GSON.fromJson(json, MapDefinition.class);
                versions.put(file.getFileName().toString(), LayoutVersion.of(map));
            }
        }
        return versions;
    }
}
