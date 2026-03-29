package net.elytrarace.setup.gui;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.MapDTO;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Handles click events for CupListGui and CupEditorGui.
 */
public class CupGuiListener implements Listener {

    private final CupService cupService;
    private final MapService mapService;
    private final Plugin plugin;

    public CupGuiListener(CupService cupService, MapService mapService, Plugin plugin) {
        this.cupService = cupService;
        this.mapService = mapService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        var holder = event.getInventory().getHolder();

        if (holder instanceof CupListGui cupListGui) {
            handleCupListClick(event, cupListGui);
        } else if (holder instanceof CupEditorGui cupEditorGui) {
            handleCupEditorClick(event, cupEditorGui);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        var holder = event.getInventory().getHolder();
        if (holder instanceof CupListGui || holder instanceof CupEditorGui) {
            event.setCancelled(true);
        }
    }

    private void handleCupListClick(InventoryClickEvent event, CupListGui gui) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        var cup = gui.getCupAtSlot(event.getSlot());
        if (cup == null) return;

        // Open editor for this cup on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            var allMaps = mapService.getMaps();
            var editor = new CupEditorGui(cup, allMaps);
            editor.open(player);
        });
    }

    private void handleCupEditorClick(InventoryClickEvent event, CupEditorGui gui) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();

        if (gui.isCupArea(slot)) {
            var map = gui.getCupMapAtSlot(slot);
            if (map == null) return;

            if (event.isShiftClick()) {
                // Remove map from cup
                removeMapFromCup(player, gui.getCup(), map);
            } else if (event.isLeftClick()) {
                // Swap: click source, then click destination (simplified: just remove+readd at end for now)
                // For a first version, shift-click removes, regular click does nothing special
                player.sendActionBar(Component.text("Shift-click to remove, or use bottom section to add maps"));
            }
        } else if (gui.isAvailableArea(slot)) {
            var map = gui.getAvailableMapAtSlot(slot);
            if (map == null) return;

            if (event.isLeftClick()) {
                // Add map to cup
                addMapToCup(player, gui.getCup(), map);
            }
        }
    }

    private void addMapToCup(Player player, FileCupDTO cup, MapDTO map) {
        var updatedMaps = new ArrayList<>(cup.maps());
        if (updatedMaps.contains(map.uuid())) return; // already in cup
        updatedMaps.add(map.uuid());

        var updatedCup = new FileCupDTO(cup.name(), cup.displayName(), updatedMaps);
        cupService.updateCup(updatedCup).thenCompose(success -> {
            if (success) {
                player.sendActionBar(Component.translatable("gui.cup.map_added")
                        .arguments(map.displayName(), cup.displayName()));
                return cupService.saveCups();
            }
            return CompletableFuture.completedFuture(null);
        }).thenRun(() ->
            // Refresh the GUI
            Bukkit.getScheduler().runTask(plugin, () -> {
                var allMaps = mapService.getMaps();
                var editor = new CupEditorGui(updatedCup, allMaps);
                editor.open(player);
            })
        );
    }

    private void removeMapFromCup(Player player, FileCupDTO cup, MapDTO map) {
        var updatedMaps = new ArrayList<>(cup.maps());
        updatedMaps.remove(map.uuid());

        var updatedCup = new FileCupDTO(cup.name(), cup.displayName(), updatedMaps);
        cupService.updateCup(updatedCup).thenCompose(success -> {
            if (success) {
                player.sendActionBar(Component.translatable("gui.cup.map_removed")
                        .arguments(map.displayName(), cup.displayName()));
                return cupService.saveCups();
            }
            return CompletableFuture.completedFuture(null);
        }).thenRun(() ->
            // Refresh the GUI
            Bukkit.getScheduler().runTask(plugin, () -> {
                var allMaps = mapService.getMaps();
                var editor = new CupEditorGui(updatedCup, allMaps);
                editor.open(player);
            })
        );
    }
}
