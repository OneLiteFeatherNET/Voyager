package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.models.ElytraMap
import net.elytrarace.models.Rings
import net.elytrarace.utils.SHOW_LINE_COUNT
import net.elytrarace.utils.SHOW_LINE_DELAY
import net.elytrarace.utils.SHOW_LINE_EXTRA
import net.elytrarace.utils.SHOW_LINE_OFFSET
import net.elytrarace.utils.SHOW_LINE_PARTICLE
import net.elytrarace.utils.SHOW_LINE_TIMER
import net.elytrarace.utils.extensions.interpolate
import org.bukkit.Location
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction

class MapService(voyager: Voyager) {
    private val linePoints = mutableMapOf<ElytraMap, List<Location>>()

    init {
        transaction {
            val maps = ElytraMap.all()
            maps.forEach { map ->
                val ringLocations = map.rings.orderBy(Rings.index to SortOrder.DESC)
                    .mapNotNull { it.locations.firstOrNull { center -> center.center }?.bukkitLocation }.windowed(6)
                // val spawnPoint = Bukkit.getWorld(map.world)?.spawnLocation ?: return@forEach
                val locs =ringLocations.map { locations ->
                    interpolate(locations, 0, 300) + interpolate(locations, 2, 300)
                }
                linePoints[map] = locs.reduce { acc, lists -> acc + lists }
            }
        }
        voyager.server.scheduler.runTaskTimerAsynchronously(voyager, showMapLines(), SHOW_LINE_DELAY, SHOW_LINE_TIMER)
    }

    private fun showMapLines() = Runnable {
        linePoints.values.forEach { mapBased ->
            mapBased.forEach {
                it.world.spawnParticle(
                    SHOW_LINE_PARTICLE,
                    it,
                    SHOW_LINE_COUNT,
                    SHOW_LINE_OFFSET,
                    SHOW_LINE_OFFSET,
                    SHOW_LINE_OFFSET,
                    SHOW_LINE_EXTRA
                )
            }
        }
    }


}