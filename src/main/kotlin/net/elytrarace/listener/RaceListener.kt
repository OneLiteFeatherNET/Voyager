package net.elytrarace.listener

import net.elytrarace.Voyager
import net.elytrarace.phases.EndPhase
import net.elytrarace.phases.LobbyPhase
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityToggleGlideEvent
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
    fun handlePlayerGlide(event: EntityToggleGlideEvent) {
        this.voyager.playerService.handlePlayerGlide(event)
    }

    @EventHandler
    fun handlePlayerMove(event: PlayerMoveEvent) {
        if (voyager.elytraPhase.currentPhase is EndPhase || voyager.elytraPhase.currentPhase is LobbyPhase) {
            if (voyager.configService.config.lobbyConfiguration.world != null && event.to.y <= voyager.configService.config.lobbyConfiguration.world!!.minHeight) {
                event.player.teleportAsync(voyager.configService.config.lobbyConfiguration.world!!.spawnLocation)
                return
            }
        }
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