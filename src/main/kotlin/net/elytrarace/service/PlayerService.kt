package net.elytrarace.service

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.elytrarace.Voyager
import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.model.dto.GameMapSession
import net.elytrarace.util.Strings
import net.elytrarace.util.TimeFormat
import net.elytrarace.utils.OBJECTIVES_NAME
import net.elytrarace.utils.api.VectorApi
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

class PlayerService(val voyager: Voyager) : VectorApi {

    val playerSessions: Int2ObjectMap<ElytraPlayer> = Int2ObjectOpenHashMap()
     fun beginGame(player: Player, mapSession: GameMapSession) {
         val elytraPlayer = ElytraPlayer(player = player, mapSession = mapSession)
         playerSessions.putIfAbsent(Integer.valueOf(player.entityId), elytraPlayer)
         mapSession.teleport(player)
         this.voyager.inventoryService.handlePlayerStart(elytraPlayer)
         Bukkit.getScheduler().runTask(voyager, Runnable {
             player.scoreboard = Bukkit.getScoreboardManager().newScoreboard
         })
     }

    fun handlePlayerQuit(player: Player) {
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        playerSessions.remove(Integer.valueOf(player.entityId))
    }

    fun handlePlayerMove(event: PlayerMoveEvent) {
        val elytraPlayer = this.playerSessions.get(Integer.valueOf(event.player.entityId)) ?: return
        val map = elytraPlayer.mapSession as GameMapSession
        val to = toVector3D(event.to)
        if (to.y <= map.world.minHeight ) {
            elytraPlayer.player.teleportAsync(map.world.spawnLocation)
            playerSessions.put(Integer.valueOf(elytraPlayer.player.entityId), elytraPlayer.copy(startTime = null, lastPortal = null))
            elytraPlayer.player.scoreboard.resetScoresFor(elytraPlayer.player)
            return
        }
        elytraPlayer.positionQueue.add(0, to)
        if (elytraPlayer.lastPortal == null) {
            val firstPortal = transaction {
                return@transaction map.sortedPortals.first()
            }
            val detected = this.voyager.detectionService.checkPlayer(elytraPlayer, firstPortal)
            if (detected) {
                elytraPlayer.player.playSound(Sound.sound { it.type(org.bukkit.Sound.BLOCK_BEACON_ACTIVATE).volume(15.0f) })
                elytraPlayer.timeStampForPortals[firstPortal] = Instant.now()
                playerSessions.put(Integer.valueOf(elytraPlayer.player.entityId), elytraPlayer.copy(startTime = Instant.now(), lastPortal = firstPortal))
                updateScoreboard(elytraPlayer)
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
                elytraPlayer.player.playSound(Sound.sound { it.type(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP) })
                elytraPlayer.timeStampForPortals[nextPortal] = Instant.now()
                playerSessions[Integer.valueOf(elytraPlayer.player.entityId)] = elytraPlayer.copy(lastPortal = nextPortal)
                updateScoreboard(elytraPlayer)
                if (nextPortal == lastPortal) {
                    playerSessions[Integer.valueOf(elytraPlayer.player.entityId)] = elytraPlayer.copy(lastPortal = nextPortal, lastTime = Instant.now())
                    elytraPlayer.player.gameMode = GameMode.SPECTATOR
                    val spectatorCheck = Bukkit.getOnlinePlayers().none { it.gameMode == GameMode.SURVIVAL }
                    if (spectatorCheck) {
                        this.voyager.elytraPhase.currentPhase.finish()
                        return
                    }

                }
            }
        }
    }

    private fun updateScoreboard(elytraPlayer: ElytraPlayer) {
        Bukkit.getScheduler().runTask(voyager, Runnable {
            val sb = elytraPlayer.player.scoreboard
            val objective = sb.getObjective(OBJECTIVES_NAME) ?: sb.registerNewObjective(
                OBJECTIVES_NAME,
                Criteria.DUMMY,
                Component.translatable("scoreboard.timings")
            )
            objective.displaySlot = DisplaySlot.SIDEBAR
            elytraPlayer.timeStampForPortals.onEachIndexed { index, entry ->
                val time = entry.value
                val startTime = elytraPlayer.startTime ?: return@onEachIndexed
                val diff = Duration.ofMillis(time.minusMillis(startTime.toEpochMilli()).toEpochMilli())
                val score = objective.getScore(Strings.getTimeString(TimeFormat.MM_SS, diff.toSeconds().toInt()) + ":${String.format("%03d", diff.toMillisPart())}")
                score.score = index + 1
            }
            elytraPlayer.player.scoreboard = sb
        })
    }

    fun handlePlayerGlide(event: EntityToggleGlideEvent) {
        val elytraPlayer = this.playerSessions.get(Integer.valueOf(event.entity.entityId)) ?: return
        if (!event.isGliding && event.entity.isOnGround) {
            elytraPlayer.player.teleportAsync(elytraPlayer.mapSession.world.spawnLocation)
            playerSessions.put(Integer.valueOf(elytraPlayer.player.entityId), elytraPlayer.copy(startTime = null, lastPortal = null))
            elytraPlayer.player.scoreboard.resetScoresFor(elytraPlayer.player)
        }
    }

}