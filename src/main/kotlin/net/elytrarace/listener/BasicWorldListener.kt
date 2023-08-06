package net.elytrarace.listener

import net.elytrarace.utils.api.CancellableListener
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBedEnterEvent

class BasicWorldListener : Listener, CancellableListener {
    @EventHandler
    fun leafDecay(event: LeavesDecayEvent) = cancelling(event)

    @EventHandler
    fun armorStandManipulateEvent(event: PlayerArmorStandManipulateEvent) = cancelling(event)

    @EventHandler
    fun bedEnterEvent(event: PlayerBedEnterEvent) = cancelling(event)

    @EventHandler
    fun blockBreak(event: BlockBreakEvent) = cancelling(event)

    @EventHandler
    fun blockPlace(event: BlockPlaceEvent) = cancelling(event)
}