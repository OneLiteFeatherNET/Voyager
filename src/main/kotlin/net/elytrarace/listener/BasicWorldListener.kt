package net.elytrarace.listener

import net.elytrarace.utils.cancelling
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent

class BasicWorldListener : Listener {

    @EventHandler
    fun leafDecay(event: LeavesDecayEvent) = cancelling(event)

    @EventHandler
    fun armorStandManipulateEvent(event: PlayerArmorStandManipulateEvent) = cancelling(event)

    @EventHandler
    fun blockBreak(event: BlockBreakEvent) = cancelling(event)

    @EventHandler
    fun blockPlace(event: BlockPlaceEvent) = cancelling(event)

}