package net.elytrarace.placeholder

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

data class PlayerProfile(
    val currentPage: Int,
    val player: Player,
    val mapSelectorInventory: Inventory
)
