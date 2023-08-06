package net.elytrarace.listener

import net.elytrarace.Voyager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

class RaceListener(val voyager: Voyager) : Listener {

    @EventHandler
    fun handlePlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        this.voyager.playerService.handlePlayerQuit(player)
    }

    @EventHandler
    fun handlePlayerMove(event: PlayerMoveEvent) {
        this.voyager.playerService.handlePlayerMove(event)
    }

    @EventHandler
    fun handleItemDamage(event: PlayerItemDamageEvent) {
        this.voyager.inventoryService.handleItemDamageEvent(event)
    }

    @EventHandler
    fun handleItemConsumeEvent(event: PlayerItemConsumeEvent) {
        this.voyager.inventoryService.handleItemConsume(event)
    }

    @EventHandler
    fun handleItemInteract(event: PlayerInteractEvent) {
        this.voyager.inventoryService.handlePlayerInteract(event)
    }

}