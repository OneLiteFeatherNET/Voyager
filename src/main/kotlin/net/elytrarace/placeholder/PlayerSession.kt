package net.elytrarace.placeholder

import net.elytrarace.models.Ring
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.time.Instant

data class PlayerSession(
    val currentPage: Int,
    val player: Player,
    val mapSelectorInventory: Inventory,
    val mapSession: MapSession,
    val startTime: Instant?,
    val lastRing: Ring? = null,
    val timeStampForRings: MutableMap<Ring, Instant> = mutableMapOf()
)