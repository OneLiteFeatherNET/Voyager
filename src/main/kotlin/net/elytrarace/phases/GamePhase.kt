package net.elytrarace.phases

import net.elytrarace.Voyager
import net.elytrarace.model.dto.GameMapSession
import net.elytrarace.phase.TickingPhase
import net.elytrarace.util.Strings
import net.elytrarace.util.TimeFormat
import net.elytrarace.utils.SHOW_LINE_COUNT
import net.elytrarace.utils.SHOW_LINE_EXTRA
import net.elytrarace.utils.SHOW_LINE_OFFSET
import net.elytrarace.utils.SHOW_LINE_PARTICLE
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant

class GamePhase(val javaPlugin: Voyager, val mapSession: GameMapSession) : TickingPhase("Game", javaPlugin, 1, true) {

    val lastPlayerPosition = mutableMapOf<Player, Location>()

    override fun onStart() {
        Bukkit.getOnlinePlayers().forEach {
            this.javaPlugin.playerService.beginGame(it, mapSession)
        }
        super.onStart()
    }
    override fun onUpdate() {
        Bukkit.getOnlinePlayers().forEach {
            val currentLoc = it.location
            val lastLoc = lastPlayerPosition.getOrPut(it) { currentLoc }
            val distance = currentLoc.distance(lastLoc)
            val blocksPerSeconds = (distance / 0.05).toInt()
            val elytraPlayer = this.javaPlugin.playerService.playerSessions.get(Integer.valueOf(it.entityId)) ?: return@forEach
            if (elytraPlayer.startTime != null) {
                val seconds = Duration.ofMillis(Instant.now().minusMillis(elytraPlayer.startTime.toEpochMilli()).toEpochMilli()).toSeconds()
                it.sendActionBar(
                    Component.translatable("actionbar.speedAndTime").args(Component.text(blocksPerSeconds), Component.text(
                        Strings.getTimeString(TimeFormat.MM_SS, seconds.toInt())))
                )
            } else {
                it.sendActionBar(
                    Component.translatable("actionbar.blocksPerSeconds").args(Component.text(blocksPerSeconds))
                )
            }
            lastPlayerPosition[it] = currentLoc
        }
        this.mapSession.splineLocations.forEach {
            this.mapSession.world.spawnParticle(
                SHOW_LINE_PARTICLE,
                it.x,
                it.y,
                it.z,
                SHOW_LINE_COUNT,
                SHOW_LINE_OFFSET,
                SHOW_LINE_OFFSET,
                SHOW_LINE_OFFSET,
                SHOW_LINE_EXTRA
            )
        }
    }
}