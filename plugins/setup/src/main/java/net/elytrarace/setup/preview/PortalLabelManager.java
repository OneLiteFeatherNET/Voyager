package net.elytrarace.setup.preview;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.*;

/**
 * Manages TextDisplay entities showing portal index labels in the world.
 * Labels are spawned when portal preview is activated and removed when deactivated.
 */
public final class PortalLabelManager {

    // Track spawned labels per world so we can remove them
    private final Map<String, List<TextDisplay>> spawnedLabels = new HashMap<>();

    /**
     * Spawns TextDisplay labels for all portals in the given world.
     */
    public void spawnLabels(World world, Collection<? extends PortalDTO> portals) {
        removeLabels(world);

        var labels = new ArrayList<TextDisplay>();
        for (var portal : portals) {
            var center = portal.locations().stream()
                    .filter(LocationDTO::center)
                    .findFirst()
                    .orElse(portal.locations().isEmpty() ? null : portal.locations().getFirst());
            if (center == null) continue;

            var location = new Location(world,
                    center.x() + 0.5, center.y() + 1.5, center.z() + 0.5);

            var textDisplay = world.spawn(location, TextDisplay.class, display -> {
                display.text(Component.text("Portal #" + portal.index(), NamedTextColor.WHITE));
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.setViewRange(0.5f); // visible from ~50 blocks
                display.setPersistent(false); // don't save to world file
            });

            labels.add(textDisplay);
        }

        spawnedLabels.put(world.getName(), labels);
    }

    /**
     * Removes all TextDisplay labels from the given world.
     */
    public void removeLabels(World world) {
        var labels = spawnedLabels.remove(world.getName());
        if (labels != null) {
            labels.forEach(display -> {
                if (display.isValid()) display.remove();
            });
        }
    }

    /**
     * Removes all labels from all worlds.
     */
    public void removeAll() {
        for (var entry : spawnedLabels.values()) {
            entry.forEach(display -> {
                if (display.isValid()) display.remove();
            });
        }
        spawnedLabels.clear();
    }

    /**
     * Checks if labels are currently spawned for a world.
     */
    public boolean hasLabels(String worldName) {
        return spawnedLabels.containsKey(worldName);
    }
}
