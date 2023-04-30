package net.elytrarace.listener

import net.elytrarace.Voyager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class MapSelectorListener(
    val voyager: Voyager
) : Listener {

    @EventHandler
    fun playerInteraction(event: PlayerInteractEvent) {
        if (event.item != voyager.inventoryService.mapSelectorItem) return
        if (event.player.world != voyager.configService.lobbyWorld?.world) return
        if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR) {
            event.player.openInventory(voyager.inventoryService.getPlayerInventory(event.player))
        }
    }

}