package net.elytrarace.common.map;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.elytrarace.common.file.FileHandler;
import net.elytrarace.common.file.GsonFileHandler;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.common.map.model.PortalDTO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Stores maps in per-world directories:
 * <pre>
 * data/maps/{worldName}/map.json      — map metadata (uuid, name, world, displayName, author)
 * data/maps/{worldName}/portals.json  — portal list
 * </pre>
 * Automatically migrates from the old single-file format (maps.json) on first load.
 */
final class MapProvider {

    private static final Logger MAP_LOGGER = LoggerFactory.getLogger(MapProvider.class);
    private static final String MAPS_FOLDER = System.getProperty("VOYAGER_MAPS_FOLDER", "maps");
    private static final String LEGACY_MAPS_FILE = "maps.json";
    private static final String MAP_FILE = "map.json";
    private static final String PORTALS_FILE = "portals.json";

    private final FileHandler fileHandler;
    private final Path mapPath;
    private final Supplier<List<FileMapDTO>> defaultMaps;
    private List<FileMapDTO> maps;

    MapProvider(@NotNull Gson gson, @NotNull Path voyagerPath, @NotNull Supplier<List<FileMapDTO>> defaultMaps) {
        this.fileHandler = new GsonFileHandler(gson);
        this.mapPath = voyagerPath.resolve(MAPS_FOLDER);
        this.defaultMaps = defaultMaps;

        if (!Files.exists(this.mapPath)) {
            try {
                Files.createDirectories(this.mapPath);
                MAP_LOGGER.info("Created maps folder at {}", this.mapPath);
            } catch (IOException e) {
                throw new IllegalStateException("Could not create maps folder at " + this.mapPath, e);
            }
        }
        migrateIfNeeded();
        loadMaps();
    }

    public static MapProvider create(@NotNull Gson gson, @NotNull Path voyagerPath, @NotNull Supplier<List<FileMapDTO>> defaultMaps) {
        return new MapProvider(gson, voyagerPath, defaultMaps);
    }

    /**
     * Migrates from old single maps.json to per-world directories.
     */
    private void migrateIfNeeded() {
        Path legacyFile = this.mapPath.resolve(LEGACY_MAPS_FILE);
        if (!Files.exists(legacyFile)) return;

        MAP_LOGGER.info("Found legacy maps.json — migrating to per-world storage");
        var legacyType = (TypeToken<List<FileMapDTO>>) TypeToken.getParameterized(List.class, FileMapDTO.class);
        var legacyMaps = this.fileHandler.load(legacyFile, legacyType);

        if (legacyMaps.isEmpty()) {
            MAP_LOGGER.warn("Legacy maps.json was empty or unreadable, skipping migration");
            return;
        }

        for (var map : legacyMaps.get()) {
            saveMapToWorld(map);
        }

        // Rename old file as backup
        try {
            Files.move(legacyFile, legacyFile.resolveSibling("maps.json.migrated"));
            MAP_LOGGER.info("Migrated {} maps to per-world storage. Old file renamed to maps.json.migrated", legacyMaps.get().size());
        } catch (IOException e) {
            MAP_LOGGER.warn("Could not rename legacy maps.json", e);
        }
    }

    public void loadMaps() {
        MAP_LOGGER.info("Loading maps from per-world directories");
        this.maps = new ArrayList<>();

        try (Stream<Path> dirs = Files.list(this.mapPath)) {
            dirs.filter(Files::isDirectory).forEach(worldDir -> {
                var mapFile = worldDir.resolve(MAP_FILE);
                var portalsFile = worldDir.resolve(PORTALS_FILE);

                if (!Files.exists(mapFile)) return;

                var mapOpt = this.fileHandler.load(mapFile, TypeToken.get(FileMapDTO.class));
                if (mapOpt.isEmpty()) {
                    MAP_LOGGER.warn("Could not load map from {}", mapFile);
                    return;
                }
                var map = mapOpt.get();

                // Load portals separately
                SortedSet<PortalDTO> portals = new TreeSet<>();
                if (Files.exists(portalsFile)) {
                    var portalsType = (TypeToken<List<FilePortalDTO>>) TypeToken.getParameterized(List.class, FilePortalDTO.class);
                    var portalsOpt = this.fileHandler.load(portalsFile, portalsType);
                    portalsOpt.ifPresent(list -> list.forEach(portals::add));
                }

                // Reconstruct full map with portals
                var fullMap = new FileMapDTO(map.uuid(), map.name(), map.world(),
                        map.displayName(), map.author(), portals);
                this.maps.add(fullMap);
            });
        } catch (IOException e) {
            MAP_LOGGER.error("Could not list map directories", e);
        }

        if (this.maps.isEmpty()) {
            this.maps = this.defaultMaps.get();
        }

        MAP_LOGGER.info("Loaded {} maps", this.maps.size());
    }

    public void saveMaps() {
        for (var map : this.maps) {
            saveMapToWorld(map);
        }
    }

    private void saveMapToWorld(FileMapDTO map) {
        var worldDir = this.mapPath.resolve(map.world());
        try {
            Files.createDirectories(worldDir);
        } catch (IOException e) {
            MAP_LOGGER.error("Could not create directory for world {}", map.world(), e);
            return;
        }

        // Save map metadata (without portals to keep files separate)
        var mapWithoutPortals = new FileMapDTO(map.uuid(), map.name(), map.world(),
                map.displayName(), map.author(), new TreeSet<>());
        this.fileHandler.save(worldDir.resolve(MAP_FILE), mapWithoutPortals, TypeToken.get(FileMapDTO.class));

        // Save portals separately
        var portalsList = new ArrayList<>(map.portals().stream()
                .map(FilePortalDTO.class::cast)
                .toList());
        var portalsType = (TypeToken<List<FilePortalDTO>>) TypeToken.getParameterized(List.class, FilePortalDTO.class);
        this.fileHandler.save(worldDir.resolve(PORTALS_FILE), portalsList, portalsType);
    }

    public void addMap(@NotNull FileMapDTO fileMapDTO) {
        this.maps.add(fileMapDTO);
    }

    public @NotNull Collection<FileMapDTO> getMaps() {
        return this.maps;
    }

    public @NotNull List<FileMapDTO> getMapsAsList() {
        return Collections.unmodifiableList(Lists.newArrayList(this.maps));
    }

    public @NotNull Path getMapPath() {
        return this.mapPath;
    }

    public void removeMap(@NotNull MapDTO mapDTO) {
        this.maps.removeIf(map -> map.uuid().equals(mapDTO.uuid()));
        // Also remove the world directory
        var worldDir = this.mapPath.resolve(mapDTO.world());
        if (Files.exists(worldDir)) {
            try {
                Files.deleteIfExists(worldDir.resolve(MAP_FILE));
                Files.deleteIfExists(worldDir.resolve(PORTALS_FILE));
                Files.deleteIfExists(worldDir);
            } catch (IOException e) {
                MAP_LOGGER.warn("Could not clean up directory for world {}", mapDTO.world(), e);
            }
        }
    }
}
