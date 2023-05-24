package net.elytrarace.listener

import net.elytrarace.Voyager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleFlightEvent

class RaceBasicListener(
    val voyager: Voyager
) : Listener {

    @EventHandler
    fun toggleFlight(event: PlayerToggleFlightEvent) {
        /*val session = voyager.playerService.playerSessions[event.player] ?: return
        if (event.isFlying && event.player.world == session.mapSession.world && session.mapSession is GameMapSession) {
            voyager.playerService.playerSessions[event.player] = session.copy(startTime = Instant.now())
        }
        if (!event.isFlying && event.player.world == session.mapSession.world && !event.player.isFlying) {
            event.player.teleportAsync(session.mapSession.world.spawnLocation)
        }*/
    }
}