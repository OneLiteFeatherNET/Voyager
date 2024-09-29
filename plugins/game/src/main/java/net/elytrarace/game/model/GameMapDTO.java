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

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;
import java.util.stream.Collectors;

public record GameMapDTO(
        UUID uuid,
        Key name,
        String world,
        World bukkitWorld,
        Component displayName,
        Component author,
        SortedSet<PortalDTO> portals,
        List<Vector3D> splinePoints
) implements MapDTO {

    private static final int SPLINE_POINTS_PER_SEGMENT = 10;
    private static final double MULTIPLAY_FACTOR = 0.001;

    public GameMapDTO(MapDTO mapDTO) {
        this(mapDTO.uuid(), mapDTO.name(), mapDTO.world(), mapDTO.displayName(), mapDTO.author(), mapDTO.portals());
    }

    public GameMapDTO(UUID uuid, Key name, String world, Component displayName, Component author, SortedSet<PortalDTO> portals) {
        this(uuid, name, world, Bukkit.getWorld(world), displayName, author, portals, generateSplinePoints(portals));
    }

    private static List<Vector3D> generateSplinePoints(SortedSet<PortalDTO> portals) {
        var centerLocations = portals.stream()
                .flatMap(portal -> portal.locations().stream())
                .filter(LocationDTO::center)
                .map(GameMapDTO::toVector3D)
                .toList();
        return WindowedStreamUtils.windowed(centerLocations, 6)
                .stream()
                .flatMap(window -> {
                    var distance = window.get(0).distanceSq(window.get(2)) ;
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
