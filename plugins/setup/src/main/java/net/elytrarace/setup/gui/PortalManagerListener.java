package net.elytrarace.setup.gui;

import net.elytrarace.common.builder.MapDTOBuilder;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.setup.undo.UndoManager;
import net.elytrarace.setup.undo.UndoOperation;
import net.elytrarace.setup.util.SetupGuard;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.TreeSet;

/**
 * Handles click events in the Portal Manager GUI.
 */
public class PortalManagerListener implements Listener {

    private final MapService mapService;
    private final UndoManager undoManager;

    public PortalManagerListener(MapService mapService, UndoManager undoManager) {
        this.mapService = mapService;
        this.undoManager = undoManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PortalManagerGui gui)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Ignore clicks outside the portal area or in player inventory
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }

        var portal = gui.getPortalAtSlot(event.getSlot());
        if (portal == null) {
            return;
        }

        if (event.isShiftClick()) {
            handleDelete(player, portal);
        } else if (event.isLeftClick()) {
            handleTeleport(player, portal);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PortalManagerGui) {
            event.setCancelled(true);
        }
    }

    private void handleTeleport(Player player, net.elytrarace.common.map.model.PortalDTO portal) {
        var center = portal.locations().stream()
                .filter(LocationDTO::center)
                .findFirst()
                .orElse(portal.locations().getFirst());

        var location = new Location(player.getWorld(),
                center.x() + 0.5, center.y() + 0.5, center.z() + 0.5);
        player.closeInventory();
        player.teleport(location);
        player.sendActionBar(Component.translatable("gui.portal.teleported")
                .arguments(Component.text(portal.index())));
    }

    private void handleDelete(Player player, net.elytrarace.common.map.model.PortalDTO portal) {
        var mapOpt = SetupGuard.getMapForWorld(mapService, player.getWorld());
        if (mapOpt.isEmpty()) return;
        var map = mapOpt.get();

        // Push to undo
        undoManager.push(player.getUniqueId(),
                new UndoOperation.DeleteOperation(map.uuid(), (FilePortalDTO) portal));

        // Remove and save
        var portals = new TreeSet<>(map.portals());
        portals.removeIf(p -> p.index() == portal.index());
        var newMap = MapDTOBuilder.create().from(map).portals(portals).build();

        mapService.updateMap(newMap).thenCompose(success -> {
            if (success) {
                player.sendActionBar(Component.translatable("success.portal.delete")
                        .arguments(Component.text(portal.index()), Component.text(portals.size())));
                return mapService.saveMaps();
            }
            return null;
        });

        player.closeInventory();
    }
}
