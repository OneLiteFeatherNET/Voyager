package net.elytrarace.service

import net.elytrarace.utils.MAP_SELECTOR_INVENTORY_TITLE
import net.elytrarace.utils.MAP_SELECTOR_ITEM_NAME
import net.elytrarace.utils.builder.itemBuilder
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class InventoryService {

    val mapSelectorItem = itemBuilder {
        material(Material.PAPER)
        displayName(MiniMessage.miniMessage().deserialize(MAP_SELECTOR_ITEM_NAME))
    }
    private val playerInventories = mutableMapOf<Player, Inventory>()

    fun getPlayerInventory(player: Player): Inventory {
        return playerInventories.getOrPut(player) {Bukkit.createInventory(null, 54, MiniMessage.miniMessage().deserialize(MAP_SELECTOR_INVENTORY_TITLE))}
    }

    fun resetPlayer(player: Player) {
        playerInventories.remove(player)
    }

}