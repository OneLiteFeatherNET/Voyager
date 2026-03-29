package net.elytrarace.setup.guide;

import com.google.gson.reflect.TypeToken;
import net.elytrarace.common.file.GsonFileHandler;
import net.elytrarace.common.map.model.GuidePointDTO;
import net.elytrarace.common.utils.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores and loads guide points per world.
 * File: data/maps/{worldName}/guides.json
 */
public final class GuidePointStore {

    private static final Logger LOG = LoggerFactory.getLogger(GuidePointStore.class);
    private static final String GUIDES_FILE = "guides.json";
    private static final TypeToken<List<GuidePointDTO>> GUIDES_TYPE =
            (TypeToken<List<GuidePointDTO>>) TypeToken.getParameterized(List.class, GuidePointDTO.class);

    private final Path mapsRoot; // data/maps/
    private final GsonFileHandler fileHandler;
    private final Map<String, List<GuidePointDTO>> cache = new ConcurrentHashMap<>();

    public GuidePointStore(Path dataPath) {
        this.mapsRoot = dataPath.resolve(System.getProperty("VOYAGER_MAPS_FOLDER", "maps"));
        this.fileHandler = new GsonFileHandler(GsonUtil.GSON);
    }

    /**
     * Gets guide points for a world, sorted by orderIndex.
     */
    public List<GuidePointDTO> getGuidePoints(String worldName) {
        return cache.computeIfAbsent(worldName, this::loadFromDisk);
    }

    /**
     * Adds a guide point for a world. Auto-saves.
     */
    public void addGuidePoint(String worldName, GuidePointDTO point) {
        var list = new ArrayList<>(getGuidePoints(worldName));
        list.add(point);
        list.sort(Comparator.naturalOrder());
        cache.put(worldName, list);
        saveToDisk(worldName, list);
    }

    /**
     * Removes a guide point by orderIndex. Returns the removed point or null.
     */
    public GuidePointDTO removeGuidePoint(String worldName, int orderIndex) {
        var list = new ArrayList<>(getGuidePoints(worldName));
        var removed = list.stream()
                .filter(g -> g.orderIndex() == orderIndex)
                .findFirst()
                .orElse(null);
        if (removed != null) {
            list.remove(removed);
            cache.put(worldName, list);
            saveToDisk(worldName, list);
        }
        return removed;
    }

    /**
     * Moves a guide point to a new position. Keeps the same orderIndex.
     * Returns the updated point, or null if not found.
     */
    public GuidePointDTO moveGuidePoint(String worldName, int orderIndex, double x, double y, double z) {
        var list = new ArrayList<>(getGuidePoints(worldName));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).orderIndex() == orderIndex) {
                var moved = new GuidePointDTO(orderIndex, x, y, z);
                list.set(i, moved);
                cache.put(worldName, list);
                saveToDisk(worldName, list);
                return moved;
            }
        }
        return null;
    }

    /**
     * Computes the next guide point orderIndex between two portal indices.
     * Portals use index*100 as orderIndex. Guide points fill gaps.
     */
    public int nextOrderIndex(String worldName, int beforePortalIndex, int afterPortalIndex) {
        int rangeStart = beforePortalIndex * 100;
        int rangeEnd = afterPortalIndex * 100;

        var existing = getGuidePoints(worldName).stream()
                .filter(g -> g.orderIndex() > rangeStart && g.orderIndex() < rangeEnd)
                .mapToInt(GuidePointDTO::orderIndex)
                .max()
                .orElse(rangeStart);

        // Place halfway between last existing point and the next portal
        return existing + (rangeEnd - existing) / 2;
    }

    public void invalidate(String worldName) {
        cache.remove(worldName);
    }

    private List<GuidePointDTO> loadFromDisk(String worldName) {
        var file = mapsRoot.resolve(worldName).resolve(GUIDES_FILE);
        if (!Files.exists(file)) return new ArrayList<>();

        return fileHandler.load(file, GUIDES_TYPE).orElse(new ArrayList<>());
    }

    private void saveToDisk(String worldName, List<GuidePointDTO> points) {
        var dir = mapsRoot.resolve(worldName);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.error("Could not create directory for world {}", worldName, e);
            return;
        }
        fileHandler.save(dir.resolve(GUIDES_FILE), points, GUIDES_TYPE);
    }
}
