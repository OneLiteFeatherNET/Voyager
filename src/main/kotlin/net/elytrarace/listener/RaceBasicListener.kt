package net.elytrarace.listener

import net.elytrarace.Voyager
import net.elytrarace.config.PluginMode
import net.elytrarace.placeholder.GameMapSession
import net.elytrarace.placeholder.LobbyMapSession
import net.elytrarace.placeholder.PlayerSession
import net.elytrarace.utils.OBJECTIVES_NAME
import net.kyori.adventure.text.Component
import org.apache.commons.geometry.euclidean.threed.Bounds3D
import org.apache.commons.geometry.euclidean.threed.Planes
import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.apache.commons.geometry.euclidean.threed.line.Lines3D
import org.apache.commons.geometry.euclidean.threed.shape.Parallelepiped
import org.apache.commons.numbers.core.Precision
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.util.Vector
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

class RaceBasicListener(
    val voyager: Voyager
) : Listener {
    private val intersectionPoint = mutableMapOf<Player, MutableList<FallingBlock>>()

    @EventHandler
    fun checkPortal(event: PlayerMoveEvent) {
        val player = event.player
        // Gets the player session
        val session = voyager.playerService.playerSessions[player] ?: return
        // Checks if the player on the lobby
        if (session.mapSession is LobbyMapSession) {
            return
        }
        // Get the game map session
        val gameMapSession = session.mapSession as GameMapSession
        // Check if last ring null - Checks if the first ring/portal
        if (session.lastRing == null) {
            // Gets the first ring
            val firstRing = transaction {
                return@transaction gameMapSession.sortedRings.first()
            }
            // Map the locations to bukkit locations
            val bukkitLocations = transaction {
                return@transaction firstRing.locations.map { it.bukkitLocation }
            }
            val to = event.to
            val from = event.from
            val found = voyager.mapService.checkPair(Pair(from, to), bukkitLocations, player) || voyager.mapService.checkPair(
                Pair(from, player.location),
                bukkitLocations,
                player
            ) || voyager.mapService.checkPair(Pair(to, player.location), bukkitLocations, player)

            if (found) {
                session.timeStampForRings[firstRing] = Instant.now()
                val newSession = session.copy(startTime = Instant.now(), lastRing = firstRing)
                voyager.mapService.updateScoreboard(newSession)
                newSession.stackPlayerPositions.push(player.location)
                voyager.playerService.playerSessions[player] = newSession
            }
        } else {
            val nextRing = transaction {
                return@transaction gameMapSession.sortedRings.higher(session.lastRing)
            }
            val latestRing = transaction {
                return@transaction gameMapSession.sortedRings.last()
            }
            val bukkitLocations = transaction {
                return@transaction (nextRing ?: latestRing).locations.map { it.bukkitLocation }
            }
            val to = event.to
            val from = event.from
            if (voyager.configService.config.pluginMode == PluginMode.TESTING) {
                if (session.startTime != null) {
                    session.stackPlayerPositions.push(player.location)
                }
            }
            val found = voyager.mapService.checkPair(Pair(from, to), bukkitLocations, player) || voyager.mapService.checkPair(
                Pair(from, player.location),
                bukkitLocations,
                player
            ) || voyager.mapService.checkPair(Pair(to, player.location), bukkitLocations, player)

            if (found && nextRing != null) {
                session.timeStampForRings[nextRing] = Instant.now()
                voyager.mapService.updateScoreboard(session)
                voyager.playerService.playerSessions[player] =
                    session.copy(lastRing = nextRing)
                if (nextRing == latestRing) {
                    this.intersectionPoint.getOrDefault(player, mutableListOf()).forEach {
                        it.remove()
                    }
                    voyager.playerService.finishMap(session)
                }
            }
        }
    }




}