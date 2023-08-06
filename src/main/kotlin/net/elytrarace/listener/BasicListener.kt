package net.elytrarace.listener

import net.elytrarace.Voyager
import net.elytrarace.utils.api.CancellableListener
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.server.ServerListPingEvent

class BasicListener(
    val voyager: Voyager
) : Listener, CancellableListener {
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

    @EventHandler
    fun handlePing(event: ServerListPingEvent) {
        voyager.cup ?: return
        event.motd(MiniMessage.miniMessage().deserialize("<green>CUP: ${voyager.cup?.displayName}"))
        event.maxPlayers = voyager.configService.config.cupConfiguration.playerSize
    }
}