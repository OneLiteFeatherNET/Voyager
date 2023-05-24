package net.elytrarace.listener

import net.elytrarace.Voyager
import net.elytrarace.utils.cancelling
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.*

class BasicPlayerListener(
    val voyager: Voyager
) : Listener {

    @EventHandler
    fun foodLevelChange(event: FoodLevelChangeEvent) = cancelling(event)

    @EventHandler
    fun dropItem(event: PlayerDropItemEvent) = cancelling(event)

    @EventHandler
    fun pickupItem(event: PlayerAttemptPickupItemEvent) = cancelling(event)

    @EventHandler
    fun entityDamage(event: EntityDamageEvent) = cancelling(event)

    @EventHandler
    fun playerEnterBed(event: PlayerBedEnterEvent) = cancelling(event)

    @EventHandler
    fun joinEvent(event: PlayerJoinEvent) {
        event.joinMessage(null)
        voyager.playerService.joinPlayer(event.player)
    }

    @EventHandler
    fun quitEvent(event: PlayerQuitEvent) {
        event.quitMessage(null)
    }
}