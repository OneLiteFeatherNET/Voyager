package net.elytrarace.game.service;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.game.model.GameMapDTO;
import net.elytrarace.game.model.GamePortalDTO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

public final class GameMapService {

    static GameMapDTO spawnTextDisplay(GameMapDTO gameMapDTO) {
        return GameMapDTO.fromSpawnedTextDisplay(gameMapDTO, gameMapDTO.portals().stream().map(GamePortalDTO.class::cast).map(portal -> GamePortalDTO.fromPortalDTO(portal, spawnTextDisplayForPortal(portal, gameMapDTO))).collect(Collectors.toCollection(TreeSet::new)));
    }

    private static TextDisplay spawnTextDisplayForPortal(GamePortalDTO portal, GameMapDTO gameMapDTO) {
        var bukkitWorld = gameMapDTO.bukkitWorld();
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
