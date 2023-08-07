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
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant

class GamePhase(val javaPlugin: Voyager, val mapSession: GameMapSession) : TickingPhase("Game", javaPlugin, 1, true) {

    private val lastPlayerPosition = mutableMapOf<Player, Location>()
    private val bossBar = BossBar.bossBar(Component.empty(), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)

    override fun onStart() {
        bossBar.addFlag(BossBar.Flag.PLAY_BOSS_MUSIC)
        Bukkit.getOnlinePlayers().forEach {
            this.javaPlugin.playerService.beginGame(it, mapSession)
            it.showBossBar(bossBar)
        }
        super.onStart()
    }

    override fun onUpdate() {
        val topThree = this.mapSession.playerSessions.values.asSequence().filter { it.lastPortal != null && it.startTime != null }.sortedByDescending { Duration.ofMillis(Instant.now().minusMillis(it.startTime?.toEpochMilli()!!).toEpochMilli()) }.sortedByDescending { it.lastPortal?.index }.take(3).map { it.player.displayName() }.toList()
        when (topThree.size) {
            1 -> bossBar.name(Component.translatable("bossbar.top3.1").args(topThree))
            2 -> bossBar.name(Component.translatable("bossbar.top3.2").args(topThree))
            3 -> bossBar.name(Component.translatable("bossbar.top3.3").args(topThree))
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

        Bukkit.getOnlinePlayers().forEach {

            val currentLoc = it.location
            val lastLoc = lastPlayerPosition.getOrPut(it) { currentLoc }
            if (currentLoc.world != lastLoc) {
                lastPlayerPosition.replace(it, currentLoc)
            }
            val distance = currentLoc.distance(lastLoc)
            val blocksPerSeconds = (distance / 0.05).toInt()
            val elytraPlayer =
                    this.mapSession.playerSessions.get(Integer.valueOf(it.entityId)) ?: return@forEach
            if (elytraPlayer.startTime != null) {
                val duration = Duration.ofMillis(Instant.now().minusMillis(elytraPlayer.startTime.toEpochMilli()).toEpochMilli())

                it.sendActionBar(
                        Component.translatable("actionbar.speedAndTime").args(
                                Component.text(blocksPerSeconds), Component.text(
                                Strings.getTimeString(TimeFormat.MM_SS, duration.toSeconds().toInt())
                        ), Component.text(
                                String.format("%03d", duration.toMillisPart())
                        )
                        )
                )
            } else {
                it.sendActionBar(
                        Component.translatable("actionbar.blocksPerSeconds").args(Component.text(blocksPerSeconds))
                )
            }
            lastPlayerPosition[it] = currentLoc
        }

    }

    override fun finish() {
        super.finish()
        Bukkit.getOnlinePlayers().forEach {
            it.hideBossBar(bossBar)
        }
    }
}