package net.elytrarace.listener

import net.elytrarace.Voyager
import net.elytrarace.utils.MAP_SELECTOR_SLOT
import net.elytrarace.utils.cancelling
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent

class BasicPlayerListener(
    val voyager: Voyager
) : Listener {

    @EventHandler
    fun foodLevelChange(event: FoodLevelChangeEvent) = cancelling(event)
    @EventHandler
    fun dropItem(event: PlayerDropItemEvent) = cancelling(event)
    @EventHandler
    fun entityDamage(event: EntityDamageEvent) = cancelling(event)
    @EventHandler
    fun playerEnterBed(event: PlayerBedEnterEvent) = cancelling(event)

    @EventHandler
    fun joinEvent(event: PlayerJoinEvent) {
        event.joinMessage(null)
        event.player.inventory.clear()
        event.player.inventory.setItem(MAP_SELECTOR_SLOT, voyager.inventoryService.mapSelectorItem)
        event.player.inventory.heldItemSlot = MAP_SELECTOR_SLOT
    }
    @EventHandler
    fun quitEvent(event: PlayerQuitEvent) {
        event.quitMessage(null)
    }

    @EventHandler
    fun loginEvent(event: PlayerLoginEvent) {
        voyager.configService.lobbyWorld?.let {
            event.player.teleportAsync(it.bukkitLocation)
        }

    }
}