package net.elytrarace.placeholder

import net.elytrarace.models.Ring
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.time.Instant
import java.util.Stack

data class PlayerSession(
    val currentPage: Int,
    val player: Player,
    val mapSelectorInventory: Inventory,
    val mapSession: MapSession,
    val startTime: Instant?,
    val lastRing: Ring? = null,
    val timeStampForRings: MutableMap<Ring, Instant> = mutableMapOf(),
    val stackPlayerPositions: Stack<Location> = Stack(),
    val intersectionPositions: Stack<Location> = Stack()
)