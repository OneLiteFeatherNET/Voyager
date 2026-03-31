package net.elytrarace.server.cup;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.map.model.BoostConfigDTO;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.cup.model.ResolvedCupDTO;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Converts shared DTOs (CupDTO, FileMapDTO, PortalDTO) into server-side domain objects
 * (CupDefinition, MapDefinition, Ring) that the game engine can use directly.
 */
public final class CupLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CupLoader.class);
    private static final int DEFAULT_RING_POINTS = 10;

    private final CupService cupService;
    private final MapService mapService;
    private final Path worldsPath;

    public CupLoader(@NotNull CupService cupService, @NotNull MapService mapService, @NotNull Path worldsPath) {
        this.cupService = cupService;
        this.mapService = mapService;
        this.worldsPath = worldsPath;
    }

    /**
     * Loads the first available cup and converts it to a {@link CupDefinition}.
     * Returns empty if no cups are configured.
     */
    public Optional<CupDefinition> loadFirstCup() {
        var cups = cupService.getCups();
        if (cups.isEmpty()) {
            LOGGER.warn("No cups configured — server will remain in lobby mode");
            return Optional.empty();
        }
        return loadCup(cups.getFirst());
    }

    /**
     * Resolves a {@link FileCupDTO} into a {@link CupDefinition} by loading all referenced maps.
     */
    public Optional<CupDefinition> loadCup(@NotNull FileCupDTO cupDTO) {
        try {
            var resolved = (ResolvedCupDTO) mapService.getMapByCup(cupDTO).get();
            var maps = new ArrayList<MapDefinition>();

            for (var mapDTO : resolved.maps()) {
                if (!(mapDTO instanceof FileMapDTO fileMapDTO)) continue;
                var mapDef = convertMap(fileMapDTO);
                mapDef.ifPresent(maps::add);
            }

            if (maps.isEmpty()) {
                LOGGER.warn("Cup '{}' resolved to zero loadable maps — skipping", cupDTO.name().asString());
                return Optional.empty();
            }

            LOGGER.info("Loaded cup '{}' with {} maps", cupDTO.name().asString(), maps.size());
            return Optional.of(new CupDefinition(cupDTO.name().asString(), maps));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while loading cup '{}'", cupDTO.name().asString(), e);
            return Optional.empty();
        } catch (ExecutionException e) {
            LOGGER.error("Failed to resolve maps for cup '{}'", cupDTO.name().asString(), e.getCause());
            return Optional.empty();
        }
    }

    /**
     * Converts a {@link FileMapDTO} to a {@link MapDefinition}.
     * Returns empty if the world directory does not exist.
     */
    private Optional<MapDefinition> convertMap(@NotNull FileMapDTO dto) {
        var worldDir = worldsPath.resolve(dto.world());
        if (!worldDir.toFile().exists()) {
            LOGGER.warn("World directory not found for map '{}': {} — skipping map",
                    dto.name().asString(), worldDir.toAbsolutePath());
            return Optional.empty();
        }

        var portals = new ArrayList<>(dto.portals());
        var rings = portals.stream()
                .map(this::convertPortalToRing)
                .toList();

        var spawnPos = deriveSpawn(portals);
        var boostConfig = convertBoostConfig(dto);
        LOGGER.info("  Map '{}' — {} rings, spawn {}, boost {}", dto.name().asString(), rings.size(), spawnPos, boostConfig);

        return Optional.of(new MapDefinition(dto.name().asString(), worldDir, rings, spawnPos, boostConfig));
    }

    /**
     * Reads boost config from the map DTO, falling back field-by-field to {@link BoostConfig#DEFAULT}.
     * Any absent JSON field is {@code null} after Gson deserialisation.
     */
    private BoostConfig convertBoostConfig(@NotNull FileMapDTO dto) {
        var raw = dto.boostConfig();
        if (raw == null) {
            return BoostConfig.DEFAULT;
        }
        double speed    = raw.speedBlocksPerTick() != null ? raw.speedBlocksPerTick() : BoostConfig.DEFAULT.speedBlocksPerTick();
        long cooldown   = raw.cooldownMs()          != null ? raw.cooldownMs()          : BoostConfig.DEFAULT.cooldownMs();
        return new BoostConfig(speed, cooldown);
    }

    /**
     * Converts a portal checkpoint into a ring.
     * The ring center is derived from the location marked as center=true.
     * The radius is the max distance from center to any edge location.
     * The normal is computed from edge vectors when possible; defaults to (0,0,1).
     */
    private Ring convertPortalToRing(@NotNull PortalDTO portal) {
        var locations = portal.locations();

        var centerLoc = locations.stream()
                .filter(LocationDTO::center)
                .findFirst()
                .orElse(locations.isEmpty() ? new LocationDTO(0, 64, 0, true) : locations.getFirst());

        var center = new Vec(centerLoc.x(), centerLoc.y(), centerLoc.z());

        var edgePoints = locations.stream()
                .filter(l -> !l.center())
                .map(l -> new Vec(l.x(), l.y(), l.z()))
                .toList();

        double radius = edgePoints.stream()
                .mapToDouble(e -> center.distance(e))
                .max()
                .orElse(3.0);

        var normal = computeNormal(center, edgePoints);

        return new Ring(center, normal, radius, DEFAULT_RING_POINTS);
    }

    /**
     * Computes the plane normal from edge points around a center.
     * Uses the cross product of the first two edge vectors when available.
     * Falls back to (0,0,1) if the normal cannot be determined.
     */
    private Vec computeNormal(@NotNull Vec center, @NotNull List<Vec> edgePoints) {
        if (edgePoints.size() < 2) {
            return new Vec(0, 0, 1);
        }
        var v1 = edgePoints.get(0).sub(center).normalize();
        var v2 = edgePoints.get(1).sub(center).normalize();
        var cross = v1.cross(v2);
        var len = cross.length();
        if (len < 1e-6) {
            return new Vec(0, 0, 1);
        }
        return cross.div(len);
    }

    /**
     * Derives a spawn position from the first portal's center location.
     * Adds 2 blocks of Y clearance so players don't spawn inside the ring.
     * Falls back to (0, 64, 0) if no portals are present.
     */
    private Pos deriveSpawn(@NotNull List<? extends PortalDTO> portals) {
        if (portals.isEmpty()) {
            return new Pos(0, 64, 0);
        }
        var first = portals.getFirst();
        var centerLoc = first.locations().stream()
                .filter(LocationDTO::center)
                .findFirst()
                .orElse(first.locations().isEmpty() ? new LocationDTO(0, 64, 0, true) : first.locations().getFirst());

        return new Pos(centerLoc.x(), centerLoc.y() + 2, centerLoc.z());
    }
}
