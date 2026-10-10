package net.elytrarace.setup.util;

import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FileMapDTO;
import net.elytrarace.setup.ElytraRace;
import net.elytrarace.setup.model.SetupHolder;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Utility for common setup-mode validation checks.
 */
public final class SetupGuard {

    private SetupGuard() {}

    /**
     * Gets the SetupHolder from the player's metadata, if they are in setup mode.
     */
    public static Optional<SetupHolder> getSetupHolder(Player player) {
        if (!player.hasMetadata(ElytraRace.SETUP_METADATA)) {
            return Optional.empty();
        }
        return Optional.ofNullable(player.getMetadata(ElytraRace.SETUP_METADATA).getFirst())
                .map(MetadataValue::value)
                .filter(SetupHolder.class::isInstance)
                .map(SetupHolder.class::cast);
    }

    /**
     * Checks if the player's current world is marked as a setup world.
     */
    public static boolean isSetupWorld(World world) {
        return world.getPersistentDataContainer().has(ElytraRace.WORLD_SETUP, PersistentDataType.BOOLEAN);
    }

    /**
     * Finds the map associated with the given world.
     */
    public static Optional<FileMapDTO> getMapForWorld(MapService mapService, World world) {
        return mapService.getMaps().stream()
                .filter(map -> map.world().equalsIgnoreCase(world.getName()))
                .filter(FileMapDTO.class::isInstance)
                .map(FileMapDTO.class::cast)
                .findFirst();
    }
}
