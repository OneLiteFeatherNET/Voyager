package net.elytrarace.game.model;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.common.utils.SplineAPI;
import net.elytrarace.common.utils.WindowedStreamUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;
import java.util.stream.Collectors;

public record GameMapDTO(UUID uuid, Key name, String world, World bukkitWorld,
                         Component displayName, Component author,
                         SortedSet<PortalDTO> portals,
                         List<Vector3D> splinePoints) implements MapDTO {

    private static final int SPLINE_POINTS_PER_SEGMENT = 10;
    private static final double MULTIPLAY_FACTOR = 0.001;


    public static GameMapDTO fromMapDTO(MapDTO mapDTO) {
        return new GameMapDTO(mapDTO.uuid(), mapDTO.name(), mapDTO.world(), Bukkit.getWorld(mapDTO.world()), mapDTO.displayName(), mapDTO.author(), mapDTO.portals().stream().map(GamePortalDTO::fromPortalDTO).collect(Collectors.toCollection(TreeSet::new)), generateSplinePoints(mapDTO.portals()));
    }

    public static GameMapDTO fromSpawnedTextDisplay(GameMapDTO gameMapDTO, SortedSet<PortalDTO> portals) {
        return new GameMapDTO(gameMapDTO.uuid(), gameMapDTO.name(), gameMapDTO.world(), gameMapDTO.bukkitWorld(), gameMapDTO.displayName(), gameMapDTO.author(), portals, gameMapDTO.splinePoints());
    }


    private static List<Vector3D> generateSplinePoints(SortedSet<PortalDTO> portals) {
        var centerLocations = portals.stream().flatMap(portal -> portal.locations().stream()).filter(LocationDTO::center).map(GameMapDTO::toVector3D).toList();
        return WindowedStreamUtils.windowed(centerLocations, 6).stream().flatMap(window -> {
            var distance = window.get(0).distanceSq(window.get(2));
            var splinePoints = new ArrayList<Vector3D>();
            int pointsPerSegment = (int) ((SPLINE_POINTS_PER_SEGMENT * (distance)) * 0.001);
            splinePoints.addAll(SplineAPI.interpolate(window, 0, pointsPerSegment));
            // splinePoints.addAll(SplineAPI.interpolate(window, 1, pointsPerSegment));
            splinePoints.addAll(SplineAPI.interpolate(window, 2, pointsPerSegment));
            return splinePoints.stream();
        }).collect(Collectors.toList());


    }

    private static Vector3D toVector3D(LocationDTO location) {
        return Vector3D.of(location.x(), location.y(), location.z());
    }
}
