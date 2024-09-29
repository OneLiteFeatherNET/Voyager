package net.elytrarace.game.model;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.game.service.PortalDetectionService;
import org.apache.commons.geometry.euclidean.threed.*;
import org.apache.commons.geometry.euclidean.threed.shape.Parallelepiped;
import org.bukkit.entity.TextDisplay;

import java.util.List;
import java.util.function.Predicate;

public record GamePortalDTO(int index,
                            List<LocationDTO> locations,
                            Plane plane,
                            Bounds3D bounds,
                            RegionBSPTree3D regionBSPTree3D,
                            TextDisplay textDisplay
) implements PortalDTO {

    public static GamePortalDTO fromPortalDTO(PortalDTO portalDTO) {
        return new GamePortalDTO(portalDTO.index(), portalDTO.locations(),
                createPlaneFromCorners(portalDTO.locations()),
                createBoundsFromCorners(portalDTO.locations()), createRegionBSPTreeFromCorners(portalDTO.locations()),
                null);
    }

    public static GamePortalDTO fromPortalDTO(PortalDTO portalDTO, TextDisplay textDisplay) {
        return new GamePortalDTO(portalDTO.index(), portalDTO.locations(),
                createPlaneFromCorners(portalDTO.locations()),
                createBoundsFromCorners(portalDTO.locations()), createRegionBSPTreeFromCorners(portalDTO.locations()),
                textDisplay);
    }


    private static RegionBSPTree3D createRegionBSPTreeFromCorners(List<LocationDTO> locations) {
        return Parallelepiped.fromBounds(createPlaneFromCorners(locations)).toTree();
    }

    private static Bounds3D createBoundsFromCorners(List<LocationDTO> locations) {
        return Bounds3D.from(locations.stream().filter(Predicate.not(LocationDTO::center)).map(GamePortalDTO::toVector3D).toList());
    }

    private static Plane createPlaneFromCorners(List<LocationDTO> locations) {
        if (locations.size() < 3) {
            throw new IllegalArgumentException("A plane requires at least 3 points");
        }
        return Planes.fromPoints(locations.stream().filter(Predicate.not(LocationDTO::center)).map(GamePortalDTO::toVector3D).toList(), PortalDetectionService.PRECISION);
    }


    private static Vector3D toVector3D(LocationDTO locationDTO) {
        return Vector3D.of(locationDTO.x(), locationDTO.y(), locationDTO.z());
    }

    public static void dispawn(GamePortalDTO portal) {
        if (portal.textDisplay != null) {
            portal.textDisplay.remove();
        }
    }
}
