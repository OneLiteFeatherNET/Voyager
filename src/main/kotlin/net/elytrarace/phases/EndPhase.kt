package net.elytrarace.phases

import net.elytrarace.Voyager
import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.phase.TickDirection
import net.elytrarace.phase.TimedPhase
import net.elytrarace.util.Strings
import net.elytrarace.util.TimeFormat
import net.elytrarace.utils.OBJECTIVES_NAME
import net.elytrarace.utils.TOP_THREE_OBJECTIVES_NAME
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard

class EndPhase(val voyager: Voyager) :
    TimedPhase("End", voyager, 20, true) {

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
        Bukkit.getScheduler().runTask(voyager, Runnable {
            val topThree = this.voyager.playerService.playerSessions.values.filter { it.lastTime != null }.sortedByDescending { it.lastTime!! }.take(3)
            Bukkit.getOnlinePlayers().forEach {
                it.gameMode = GameMode.SURVIVAL
                it.teleportAsync(this.voyager.configService.config.lobbyConfiguration.bukkitLocation)
                it.inventory.clear()
                val sb = Bukkit.getScoreboardManager().mainScoreboard
                showTopThreePlayers(sb, topThree)
                it.scoreboard = sb
            }
        })
        super.onStart()
    }

    private fun showTopThreePlayers(scoreboard: Scoreboard, topThree: List<ElytraPlayer>) {
        val objective = scoreboard.getObjective(TOP_THREE_OBJECTIVES_NAME) ?: scoreboard.registerNewObjective(TOP_THREE_OBJECTIVES_NAME, Criteria.DUMMY, Component.translatable("scoreboard.topThree"))
        objective.displaySlot = DisplaySlot.SIDEBAR
        topThree.forEachIndexed { index, elytraPlayer ->
            objective.getScore(elytraPlayer.player.name).score = index + 1
        }
    }

}