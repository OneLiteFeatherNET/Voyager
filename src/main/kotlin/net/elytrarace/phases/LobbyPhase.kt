package net.elytrarace.phases

import net.elytrarace.Voyager
import net.elytrarace.model.config.CupConfiguration
import net.elytrarace.phase.TickDirection
import net.elytrarace.phase.TimedPhase
import net.elytrarace.util.Strings
import net.elytrarace.util.TimeFormat
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit

class LobbyPhase(val voyager: Voyager, private val cupConfiguration: CupConfiguration) :
    TimedPhase("Lobby", voyager, 20, true) {

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
                Component.translatable("plugin.phase.lobby.current").args(Component.text(time))
            )
        }
        if (players.size < this.cupConfiguration.minPlayerSize) currentTicks = 121

    }

    override fun onFinish() {

    }

    override fun onStart() {
        currentTicks = 121
        super.onStart()
    }

}