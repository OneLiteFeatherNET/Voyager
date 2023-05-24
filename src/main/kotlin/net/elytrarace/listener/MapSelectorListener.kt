package net.elytrarace.listener

import net.elytrarace.Voyager
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerInteractEvent

class MapSelectorListener(
    val voyager: Voyager
) : Listener {

    @EventHandler
    fun playerInteraction(event: PlayerInteractEvent) {
        if (event.item != voyager.inventoryService.mapSelectorItem) return
        if (event.player.world != voyager.configService.lobbyWorld?.world) return
        if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR) {
            voyager.playerService.openSelector(player = event.player)
        }
    }

    @EventHandler
    fun playerInventoryInteractionEvent(event: InventoryClickEvent) {
        if (event.whoClicked.inventory == event.clickedInventory) {
            event.result = Event.Result.DENY
            event.isCancelled = true
            return
        }
        val whoClicked = event.whoClicked
        if (whoClicked is Player) {

            val session = voyager.playerService.playerSessions[whoClicked] ?: return
            val slot = event.slot
            if (session.mapSelectorInventory == event.inventory) {
                val page = voyager.inventoryService.itemsPerPage[session.currentPage]
                val map = page[slot]
                voyager.playerService.joinMap(map, session)
                event.result = Event.Result.DENY
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun playerInventoryDragEvent(event: InventoryDragEvent) {
        event.isCancelled = true
        event.result = Event.Result.DENY
    }

}