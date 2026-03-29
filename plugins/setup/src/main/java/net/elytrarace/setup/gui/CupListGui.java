package net.elytrarace.setup.gui;

import net.elytrarace.common.cup.model.FileCupDTO;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Chest GUI showing all cups. Click a cup to open its editor.
 */
public class CupListGui implements InventoryHolder {

    private static final int SIZE = 54;

    private final Inventory inventory;
    private final List<FileCupDTO> cupList;

    public CupListGui(List<FileCupDTO> cups) {
        this.cupList = new ArrayList<>(cups);
        this.inventory = Bukkit.createInventory(this, SIZE,
                Component.text("Cup Manager", NamedTextColor.DARK_GRAY));
        populate();
    }

    private void populate() {
        inventory.clear();
        for (int i = 0; i < cupList.size() && i < SIZE; i++) {
            inventory.setItem(i, createCupItem(cupList.get(i)));
        }
    }

    private ItemStack createCupItem(FileCupDTO cup) {
        var item = new ItemStack(Material.GOLD_INGOT);
        var meta = item.getItemMeta();

        meta.displayName(cup.displayName().decoration(TextDecoration.ITALIC, false));

        var lore = List.of(
                Component.text(cup.name().asString(), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Maps: " + cup.maps().size(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to edit", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /**
     * Returns the cup at the given slot, or null.
     */
    public FileCupDTO getCupAtSlot(int slot) {
        if (slot < 0 || slot >= cupList.size()) return null;
        return cupList.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
