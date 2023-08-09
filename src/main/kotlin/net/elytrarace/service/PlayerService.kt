package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.model.dto.GameMapSession
import net.elytrarace.phase.GamePhase
import net.elytrarace.phases.EndPhase
import net.elytrarace.phases.LobbyPhase
import net.elytrarace.util.Strings
import net.elytrarace.util.TimeFormat
import net.elytrarace.utils.CUP_OBJECTIVES_NAME
import net.elytrarace.utils.OBJECTIVES_NAME
import net.elytrarace.utils.api.VectorApi
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
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

    fun beginGame(player: Player, mapSession: GameMapSession) {
        val elytraPlayer = ElytraPlayer(player = player, mapSession = mapSession)
        mapSession.playerSessions.putIfAbsent(Integer.valueOf(player.entityId), elytraPlayer)
        mapSession.teleport(player)
        this.voyager.inventoryService.handlePlayerStart(elytraPlayer)
        Bukkit.getScheduler().runTask(voyager, Runnable {
            player.gameMode = GameMode.SURVIVAL
            val sb = if (player.scoreboard == Bukkit.getScoreboardManager().mainScoreboard) {
                Bukkit.getScoreboardManager().newScoreboard
            } else {
                player.scoreboard
            }
            sb.clearSlot(DisplaySlot.SIDEBAR)
            val objective = sb.getObjective(OBJECTIVES_NAME) ?: sb.registerNewObjective(
                    OBJECTIVES_NAME,
                    Criteria.DUMMY,
                    Component.translatable("scoreboard.timings")
            )
            objective.displaySlot = DisplaySlot.SIDEBAR
            player.scoreboard = sb
        })
    }

    fun handlePlayerQuit(player: Player) {
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val phase = this.voyager.elytraPhase.currentPhase
        if (phase is LobbyPhase || phase is EndPhase) return
        val gamePhase = phase as net.elytrarace.phases.GamePhase
        gamePhase.mapSession.playerSessions.remove(Integer.valueOf(player.entityId))
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            Bukkit.shutdown()
            return
        }
        val spectatorCheck = Bukkit.getOnlinePlayers().none { it.gameMode == GameMode.SURVIVAL }
        if (spectatorCheck) {
            handleFinishOfMap()
            return
        }

    }

    fun handlePlayerJoinLobbyPhase(player: Player) {
        val scoreboard = player.scoreboard
        scoreboard.clearSlot(DisplaySlot.SIDEBAR)
        voyager.cup ?: return
        val objective = scoreboard.getObjective(CUP_OBJECTIVES_NAME) ?: scoreboard.registerNewObjective(
                CUP_OBJECTIVES_NAME,
                Criteria.DUMMY,
                Component.translatable("scoreboard.cup").args(MiniMessage.miniMessage().deserialize(voyager.cup?.displayName
                        ?: ""))
        )
        objective.displaySlot = DisplaySlot.SIDEBAR
        val score = objective.getScore("Maps")
        score.score = voyager.playableMaps.size
    }

    fun handlePlayerMove(event: PlayerMoveEvent) {
        val phase = this.voyager.elytraPhase.currentPhase ?: return
        if (phase is LobbyPhase || phase is EndPhase) return
        val gamePhase = phase as net.elytrarace.phases.GamePhase
        val elytraPlayer = gamePhase.mapSession.playerSessions.get(Integer.valueOf(event.player.entityId)) ?: return
        val map = elytraPlayer.mapSession as GameMapSession
        val to = toVector3D(event.to)
        if (to.y <= map.world.minHeight && this.voyager.elytraPhase.currentPhase is GamePhase && elytraPlayer.lastTime == null) {
            elytraPlayer.player.teleportAsync(map.world.spawnLocation)
            gamePhase.mapSession.playerSessions.put(Integer.valueOf(elytraPlayer.player.entityId), elytraPlayer.copy(startTime = null, lastPortal = null, timeStampForPortals = mutableMapOf(), positionQueue = ArrayList()))
            val objective = elytraPlayer.player.scoreboard.getObjective(OBJECTIVES_NAME) ?: return
            objective.unregister()
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
                val newValue = elytraPlayer.copy(startTime = Instant.now(), lastPortal = firstPortal)
                gamePhase.mapSession.playerSessions.put(Integer.valueOf(elytraPlayer.player.entityId), newValue)
                Bukkit.getScheduler().runTask(voyager, updateScoreboard(newValue))
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
                gamePhase.mapSession.playerSessions[Integer.valueOf(elytraPlayer.player.entityId)] = elytraPlayer.copy(lastPortal = nextPortal)
                Bukkit.getScheduler().runTask(voyager, updateScoreboard(elytraPlayer))
                if (nextPortal == lastPortal) {
                    val lastTime = Instant.now()
                    val diffTime = Duration.ofMillis(elytraPlayer.startTime?.toEpochMilli()!!).minusMillis(lastTime.toEpochMilli())
                    gamePhase.mapSession.playerSessions[Integer.valueOf(elytraPlayer.player.entityId)] = elytraPlayer.copy(lastPortal = nextPortal, lastTime = lastTime, timeDiff = diffTime)
                    elytraPlayer.player.gameMode = GameMode.SPECTATOR
                    val spectatorCheck = Bukkit.getOnlinePlayers().none { it.gameMode == GameMode.SURVIVAL }
                    if (spectatorCheck) {
                        handleFinishOfMap()
                        return
                    }

                }
            }
        }
    }

    private fun handleFinishOfMap() {
        this.voyager.elytraPhase.currentPhase.finish()
    }

    private fun updateScoreboard(elytraPlayer: ElytraPlayer) = Runnable {
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
    }

    fun handlePlayerGlide(event: EntityToggleGlideEvent) {
        val phase = this.voyager.elytraPhase.currentPhase
        if (phase is LobbyPhase || phase is EndPhase) return
        val gamePhase = phase as net.elytrarace.phases.GamePhase
        val elytraPlayer = gamePhase.mapSession.playerSessions.get(Integer.valueOf(event.entity.entityId)) ?: return
        if (!event.isGliding && event.entity.isOnGround && this.voyager.elytraPhase.currentPhase is GamePhase && elytraPlayer.lastTime == null) {
            elytraPlayer.player.teleportAsync(elytraPlayer.mapSession.world.spawnLocation)
            gamePhase.mapSession.playerSessions.put(Integer.valueOf(elytraPlayer.player.entityId), elytraPlayer.copy(startTime = null, lastPortal = null, timeStampForPortals = mutableMapOf(), positionQueue = ArrayList()))
            val objective = elytraPlayer.player.scoreboard.getObjective(OBJECTIVES_NAME) ?: return
            objective.unregister()
        }
    }

}