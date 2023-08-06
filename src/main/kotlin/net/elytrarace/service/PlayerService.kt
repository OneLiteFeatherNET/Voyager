package net.elytrarace.service

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.elytrarace.Voyager
import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.model.dto.GameMapSession
import net.elytrarace.utils.api.VectorApi
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class PlayerService(val voyager: Voyager) : VectorApi {

    val playerSessions: Int2ObjectMap<ElytraPlayer> = Int2ObjectOpenHashMap()
     fun beginGame(player: Player, mapSession: GameMapSession) {
         val elytraPlayer = ElytraPlayer(player = player, mapSession = mapSession)
         playerSessions.putIfAbsent(Integer.valueOf(player.entityId), elytraPlayer)
         mapSession.teleport(player)
         this.voyager.inventoryService.handlePlayerStart(elytraPlayer)
     }

    fun handlePlayerQuit(player: Player) {
        playerSessions.remove(Integer.valueOf(player.entityId), playerSessions.get(Integer.valueOf(player.entityId)) as Any)
    }

    fun handlePlayerMove(event: PlayerMoveEvent) {
        val elytraPlayer = this.playerSessions.get(Integer.valueOf(event.player.entityId)) ?: return
        val map = elytraPlayer.mapSession as GameMapSession
        val to = toVector3D(event.to)
        elytraPlayer.positionQueue.add(0, to)
        if (elytraPlayer.lastPortal == null) {
            val firstPortal = transaction {
                return@transaction map.sortedPortals.first()
            }
            val detected = this.voyager.detectionService.checkPlayer(elytraPlayer, firstPortal)
            if (detected) {
                elytraPlayer.timeStampForPortals[firstPortal] = Instant.now()
                playerSessions.put(Integer.valueOf(elytraPlayer.player.entityId), elytraPlayer.copy(startTime = Instant.now(), lastPortal = firstPortal))
            }
        } else {
            val nextPortal = transaction {
                return@transaction map.sortedPortals.higher(elytraPlayer.lastPortal)
            } ?: return // TODO: Better Handling - The game can be not successfully end
            val lastPortal = transaction {
                return@transaction map.sortedPortals.last()
            }
            val detected = this.voyager.detectionService.checkPlayer(elytraPlayer, nextPortal)
            if (detected) {
                elytraPlayer.timeStampForPortals[nextPortal] = Instant.now()
                playerSessions[Integer.valueOf(elytraPlayer.player.entityId)] = elytraPlayer.copy(lastPortal = nextPortal)
                if (nextPortal == lastPortal) {
                    elytraPlayer.player.gameMode = GameMode.SPECTATOR
                    val spectatorCheck = Bukkit.getOnlinePlayers().none { it.gameMode == GameMode.SURVIVAL }
                    if (spectatorCheck) {
                        this.voyager.elytraPhase.currentPhase.finish()
                    }
                }
            }
        }
    }

}