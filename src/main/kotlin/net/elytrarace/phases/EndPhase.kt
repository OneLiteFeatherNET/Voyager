package net.elytrarace.phases

import net.elytrarace.Voyager
import net.elytrarace.phase.TickDirection
import net.elytrarace.phase.TimedPhase
import net.elytrarace.util.Strings
import net.elytrarace.util.TimeFormat
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode

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
        Bukkit.getOnlinePlayers().forEach {
            it.gameMode = GameMode.SURVIVAL
            it.teleportAsync(this.voyager.configService.config.lobbyConfiguration.bukkitLocation)
            it.inventory.clear()

        }
        super.onStart()
    }

}