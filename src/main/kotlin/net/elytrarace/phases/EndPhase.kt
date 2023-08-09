package net.elytrarace.phases

import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.elytrarace.Voyager
import net.elytrarace.phase.TickDirection
import net.elytrarace.phase.TimedPhase
import net.elytrarace.util.Strings
import net.elytrarace.util.TimeFormat
import net.elytrarace.utils.TOP_THREE_OBJECTIVES_NAME
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard

class EndPhase(val voyager: Voyager) :
    TimedPhase("End", voyager, 20, true) {
    val playerPoints: Object2IntMap<Player> = Object2IntOpenHashMap()

    init {
        endTicks = 0
        tickDirection = TickDirection.DOWN
        // isPaused = true
    }

    override fun onUpdate() {
        val players = Bukkit.getOnlinePlayers()
        val time = Strings.getTimeString(TimeFormat.MM_SS, currentTicks)
        val sound = when (currentTicks) {
            60,
            20,
            15,
            10,
            5,
            4,
            3,
            2,
            1 -> Sound.sound { it.type(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS).volume(2.0f) }

            else -> null
        }

        players.forEach {
            if (sound != null) {
                it.playSound(sound)
            }
            it.sendActionBar(
                Component.translatable("plugin.phase.end.current").args(Component.text(time))
            )
        }

    }

    override fun onFinish() {
        Bukkit.shutdown()
    }

    override fun onStart() {
        currentTicks = 60
        this.voyager.playableMaps.forEach { gameMapSession ->
            gameMapSession.playerSessions.values.forEach { elytraPlayer ->
                val points = this.playerPoints.getOrPut(elytraPlayer.player) {0}
                this.playerPoints.put(elytraPlayer.player, points + (elytraPlayer.points ?: 0))
            }
        }
        val topThreePlayers = this.playerPoints.toList().sortedByDescending { pair -> pair.second }.take(3).toMap()

        Bukkit.getScheduler().runTask(voyager, Runnable {
            Bukkit.getOnlinePlayers().forEach {
                it.gameMode = GameMode.SURVIVAL
                it.teleportAsync(this.voyager.configService.config.lobbyConfiguration.bukkitLocation)
                it.inventory.clear()
                val sb = Bukkit.getScoreboardManager().mainScoreboard
                showTopThreePlayers(sb, topThreePlayers)
                it.scoreboard = sb
            }
        })
        super.onStart()
    }

    private fun showTopThreePlayers(scoreboard: Scoreboard, topThree: Map<Player, Int>) {
        val objective = scoreboard.getObjective(TOP_THREE_OBJECTIVES_NAME) ?: scoreboard.registerNewObjective(TOP_THREE_OBJECTIVES_NAME, Criteria.DUMMY, Component.translatable("scoreboard.topThree"))
        objective.displaySlot = DisplaySlot.SIDEBAR
        topThree.forEach { (player, points) ->
            objective.getScore(player.name).score = points
        }
    }

}