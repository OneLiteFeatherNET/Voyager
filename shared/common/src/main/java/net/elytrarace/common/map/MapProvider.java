package net.elytrarace.common.map;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.elytrarace.common.file.FileHandler;
import net.elytrarace.common.file.GsonFileHandler;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.map.model.MapDTO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class MapProvider {

    private static final Logger MAP_LOGGER = LoggerFactory.getLogger(MapProvider.class);
    private static final String MAPS_FOLDER = System.getProperty("VOYAGER_MAPS_FOLDER", "maps");
    private static final String MAPS_FILE = "maps.json";

    private final FileHandler fileHandler;
    private final Path mapPath;
    private final Supplier<List<FileMapDTO>> defaultMaps;
    private List<FileMapDTO> maps;

    MapProvider(@NotNull Gson gson, @NotNull Path voyagerPath, @NotNull Supplier<List<FileMapDTO>> defaultMaps) {
        this.fileHandler = new GsonFileHandler(gson);
        this.mapPath = voyagerPath.resolve(MAPS_FOLDER);
        this.defaultMaps = defaultMaps;

        if (!Files.exists(this.mapPath)) {
            throw new IllegalStateException("The maps folder does not exist. Please name the cup folder " + MAPS_FOLDER);
        }
        loadMaps();
    }

    public static MapProvider create(@NotNull Gson gson, @NotNull Path voyagerPath, @NotNull Supplier<List<FileMapDTO>> defaultMaps) {
        return new MapProvider(gson, voyagerPath, defaultMaps);
    }

    public void loadMaps() {
        MAP_LOGGER.info("Starting to load maps of the game");
        final Path mapFile = this.mapPath.resolve(MAPS_FILE);
        if (!Files.exists(mapFile)) {
            MAP_LOGGER.error("The maps file does not exist");
            MAP_LOGGER.info("Creating a new maps file");
            this.maps = this.defaultMaps.get();
            return;
        }

        final Optional<List<FileMapDTO>> optionalMap = this.fileHandler.load(mapFile, (TypeToken<List<FileMapDTO>>) TypeToken.getParameterized(List.class, FileMapDTO.class));

        if (optionalMap.isEmpty()) {
            throw new IllegalStateException("The cups could not be loaded");
        }

        this.maps = optionalMap.get();
    }


    public void saveMaps() {
        this.fileHandler.save(this.mapPath.resolve(MAPS_FILE), maps, (TypeToken<List<FileMapDTO>>) TypeToken.getParameterized(List.class, FileMapDTO.class));
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
    }
}
