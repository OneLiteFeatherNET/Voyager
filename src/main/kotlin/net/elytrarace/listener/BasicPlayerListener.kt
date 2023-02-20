package net.elytrarace.listener

import net.elytrarace.utils.cancelling
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerDropItemEvent

class BasicPlayerListener : Listener {

    @EventHandler
    fun foodLevelChange(event: FoodLevelChangeEvent) = cancelling(event)
    @EventHandler
    fun dropItem(event: PlayerDropItemEvent) = cancelling(event)
    @EventHandler
    fun entityDamage(event: EntityDamageEvent) = cancelling(event)
}