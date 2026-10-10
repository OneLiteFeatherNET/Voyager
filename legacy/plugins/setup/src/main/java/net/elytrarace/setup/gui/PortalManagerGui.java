package net.elytrarace.setup.gui;

import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
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
import java.util.SortedSet;

/**
 * Chest GUI displaying all portals for a map. Each portal is a colored glass pane.
 * Left-click to teleport, shift-click to delete.
 */
public class PortalManagerGui implements InventoryHolder {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9; // 54 slots
    private static final int PORTAL_SLOTS = SIZE - 9; // bottom row reserved for info

    /** Glass pane materials cycling by portal index for visual distinction. */
    private static final Material[] PANE_COLORS = {
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.WHITE_STAINED_GLASS_PANE,
    };

    private final Inventory inventory;
    private final List<PortalDTO> portalList;
    private final Component mapDisplayName;

    public PortalManagerGui(SortedSet<? extends PortalDTO> portals, Component mapDisplayName) {
        this.mapDisplayName = mapDisplayName;
        this.portalList = new ArrayList<>(portals);
        this.inventory = Bukkit.createInventory(this, SIZE,
                GlobalTranslator.render(Component.translatable("gui.portal.title", mapDisplayName), Locale.US));
        populate();
    }

    private void populate() {
        inventory.clear();

        // Fill portal slots
        for (int i = 0; i < portalList.size() && i < PORTAL_SLOTS; i++) {
            var portal = portalList.get(i);
            inventory.setItem(i, createPortalItem(portal, i));
        }

        // Bottom row: info item in center
        var infoItem = new ItemStack(Material.BOOK);
        var infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Component.translatable("gui.portal.info.item.name")
                .decoration(TextDecoration.ITALIC, false));
        var infoLore = List.of(
                Component.translatable("gui.portal.info.portal_count",
                        Component.text(portalList.size()))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.translatable("gui.portal.info.left_click")
                        .decoration(TextDecoration.ITALIC, false),
                Component.translatable("gui.portal.info.shift_click")
                        .decoration(TextDecoration.ITALIC, false)
        );
        infoMeta.lore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inventory.setItem(SIZE - 5, infoItem); // center of bottom row
    }

    private ItemStack createPortalItem(PortalDTO portal, int slotIndex) {
        var material = PANE_COLORS[slotIndex % PANE_COLORS.length];
        var item = new ItemStack(material);
        var meta = item.getItemMeta();

        meta.displayName(Component.translatable("gui.portal.item.name",
                Component.text(portal.index()))
                .decoration(TextDecoration.ITALIC, false));

        var lore = new ArrayList<Component>();

        // Center position
        portal.locations().stream()
                .filter(LocationDTO::center)
                .findFirst()
                .ifPresent(center -> lore.add(
                        Component.translatable("gui.portal.item.center",
                                Component.text(center.x()),
                                Component.text(center.y()),
                                Component.text(center.z()))
                                .decoration(TextDecoration.ITALIC, false)
                ));

        // Vertex count
        long vertexCount = portal.locations().stream().filter(loc -> !loc.center()).count();
        lore.add(Component.translatable("gui.portal.item.vertices",
                Component.text(vertexCount))
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.empty());
        lore.add(Component.translatable("gui.portal.item.left_click")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.translatable("gui.portal.item.shift_click")
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /**
     * Returns the portal at the given inventory slot, or null if the slot has no portal.
     */
    public PortalDTO getPortalAtSlot(int slot) {
        if (slot < 0 || slot >= portalList.size()) {
            return null;
        }
        return portalList.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
