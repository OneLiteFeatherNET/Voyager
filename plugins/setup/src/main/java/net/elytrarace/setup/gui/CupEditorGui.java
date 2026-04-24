package net.elytrarace.setup.gui;

import net.elytrarace.common.cup.model.FileCupDTO;
import net.elytrarace.common.map.model.MapDTO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Chest GUI for editing a cup's map list.
 * Top rows: maps in this cup (ordered). Separator. Bottom rows: available maps not in cup.
 */
public class CupEditorGui implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int SEPARATOR_ROW = 3; // row index (0-based), so slots 27-35
    private static final int CUP_MAP_START = 0;
    private static final int CUP_MAP_END = 27; // exclusive — rows 0-2 = 27 slots
    private static final int AVAILABLE_START = 36; // rows 4-5 = 18 slots
    private static final int AVAILABLE_END = 54;

    /** Wool colors for cup maps — green for first, yellow middle, red last. */
    private static final Material[] POSITION_MATERIALS = {
            Material.LIME_WOOL,
            Material.YELLOW_WOOL,
            Material.ORANGE_WOOL,
            Material.RED_WOOL,
    };

    private final Inventory inventory;
    private final FileCupDTO cup;
    private final List<MapDTO> cupMaps;       // maps in cup, ordered
    private final List<MapDTO> availableMaps; // maps not in any cup

    public CupEditorGui(FileCupDTO cup, List<MapDTO> allMaps) {
        this.cup = cup;

        // Resolve cup maps in order
        this.cupMaps = new ArrayList<>();
        for (UUID mapUuid : cup.maps()) {
            allMaps.stream()
                    .filter(m -> m.uuid().equals(mapUuid))
                    .findFirst()
                    .ifPresent(cupMaps::add);
        }

        // Available = all maps not in this cup
        var cupUuids = cup.maps();
        this.availableMaps = allMaps.stream()
                .filter(m -> !cupUuids.contains(m.uuid()))
                .toList();

        this.inventory = Bukkit.createInventory(this, SIZE,
                GlobalTranslator.render(Component.translatable("gui.cup.editor.title", cup.displayName()), Locale.US));
        populate();
    }

    private void populate() {
        inventory.clear();

        // Top rows: maps in cup
        for (int i = 0; i < cupMaps.size() && i < CUP_MAP_END; i++) {
            inventory.setItem(CUP_MAP_START + i, createCupMapItem(cupMaps.get(i), i));
        }

        // Separator row (row 3)
        var separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var sepMeta = separator.getItemMeta();
        sepMeta.displayName(Component.translatable("gui.cup.editor.separator")
                .decoration(TextDecoration.ITALIC, false));
        separator.setItemMeta(sepMeta);
        for (int slot = SEPARATOR_ROW * 9; slot < (SEPARATOR_ROW + 1) * 9; slot++) {
            inventory.setItem(slot, separator);
        }

        // Bottom rows: available maps
        for (int i = 0; i < availableMaps.size() && i < (AVAILABLE_END - AVAILABLE_START); i++) {
            inventory.setItem(AVAILABLE_START + i, createAvailableMapItem(availableMaps.get(i)));
        }
    }

    private ItemStack createCupMapItem(MapDTO map, int positionInCup) {
        int totalMaps = cupMaps.size();
        Material material;
        if (totalMaps <= 1) {
            material = POSITION_MATERIALS[0]; // single map = green
        } else {
            // Scale position to 0-3 range
            int colorIndex = (int) ((double) positionInCup / (totalMaps - 1) * (POSITION_MATERIALS.length - 1));
            material = POSITION_MATERIALS[Math.min(colorIndex, POSITION_MATERIALS.length - 1)];
        }

        var item = new ItemStack(material);
        var meta = item.getItemMeta();

        meta.displayName(map.displayName().decoration(TextDecoration.ITALIC, false));
        var lore = List.of(
                Component.translatable("gui.cup.editor.item.position",
                        Component.text(positionInCup + 1),
                        Component.text(totalMaps))
                        .decoration(TextDecoration.ITALIC, false),
                Component.translatable("gui.cup.editor.item.world",
                        Component.text(map.world()))
                        .decoration(TextDecoration.ITALIC, false),
                Component.translatable("gui.cup.editor.item.portals",
                        Component.text(map.portals().size()))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.translatable("gui.cup.editor.item.swap")
                        .decoration(TextDecoration.ITALIC, false),
                Component.translatable("gui.cup.editor.item.remove")
                        .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAvailableMapItem(MapDTO map) {
        var item = new ItemStack(Material.WHITE_WOOL);
        var meta = item.getItemMeta();

        meta.displayName(map.displayName().decoration(TextDecoration.ITALIC, false));
        var lore = List.of(
                Component.translatable("gui.cup.editor.item.world",
                        Component.text(map.world()))
                        .decoration(TextDecoration.ITALIC, false),
                Component.translatable("gui.cup.editor.item.portals",
                        Component.text(map.portals().size()))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.translatable("gui.cup.editor.available.add")
                        .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns the map in the cup at the given slot, or null if slot is outside cup area.
     */
    public MapDTO getCupMapAtSlot(int slot) {
        int index = slot - CUP_MAP_START;
        if (index < 0 || index >= cupMaps.size()) return null;
        return cupMaps.get(index);
    }

    /**
     * Returns the available map at the given slot, or null.
     */
    public MapDTO getAvailableMapAtSlot(int slot) {
        int index = slot - AVAILABLE_START;
        if (index < 0 || index >= availableMaps.size()) return null;
        return availableMaps.get(index);
    }

    /**
     * Returns the cup slot index (0-based position in cup) for a given inventory slot.
     */
    public int getCupPositionForSlot(int slot) {
        return slot - CUP_MAP_START;
    }

    public boolean isCupArea(int slot) {
        return slot >= CUP_MAP_START && slot < CUP_MAP_END;
    }

    public boolean isAvailableArea(int slot) {
        return slot >= AVAILABLE_START && slot < AVAILABLE_END;
    }

    public FileCupDTO getCup() {
        return cup;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
