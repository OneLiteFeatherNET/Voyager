package net.elytrarace.game.model;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.game.service.PortalDetectionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.apache.commons.geometry.euclidean.threed.*;
import org.apache.commons.geometry.euclidean.threed.shape.Parallelepiped;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public record GamePortalDTO(int index,
                            List<LocationDTO> locations,
                            Plane plane,
                            Bounds3D bounds,
                            RegionBSPTree3D regionBSPTree3D,
                            TextDisplay textDisplay
) implements PortalDTO {

    public static GamePortalDTO fromPortalDTO(PortalDTO portalDTO, World world) {
        return new GamePortalDTO(portalDTO.index(), portalDTO.locations(),
                createPlaneFromCorners(portalDTO.locations()),
                createBoundsFromCorners(portalDTO.locations()), createRegionBSPTreeFromCorners(portalDTO.locations()),
                spawnTextDisplayForPortal(portalDTO, world));
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

    public static void despawn(GamePortalDTO portal) {
        if (portal.textDisplay != null) {
            portal.textDisplay.remove();
        }
    }

    private static TextDisplay spawnTextDisplayForPortal(PortalDTO portal, World bukkitWorld) {
        Optional<LocationDTO> centerLocation = portal.locations().stream().filter(LocationDTO::center).findFirst();
        return centerLocation.map(locationDTO -> bukkitWorld.spawn(new Location(bukkitWorld, locationDTO.x(), locationDTO.y(), locationDTO.z()), TextDisplay.class, textDisplay -> {
            textDisplay.text(Component.text("Portal " + portal.index()).color(portal.index() == 1 ? TextColor.color(0x00FF00) : TextColor.color(0xFF0000)));
            textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setDisplayHeight(10);
            textDisplay.setGlowing(true);
            textDisplay.setDefaultBackground(false);
        })).orElse(null);
    }
}
