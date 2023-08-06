package net.elytrarace.model.dto

import net.elytrarace.model.dbo.ElytraMap
import net.elytrarace.model.dbo.Portal
import net.elytrarace.model.dbo.Portals
import net.elytrarace.utils.POINTS_PER_SEGMENT
import net.elytrarace.utils.api.SplineApi
import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.bukkit.World
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class GameMapSession(world: World, val elytraMap: ElytraMap) : MapSession(world), SplineApi {
    val sortedPortals: TreeSet<PortalDTO> by lazy { buildSortedPortals(elytraMap) }
    val splineLocations: List<Vector3D> by lazy { calculateSpline(elytraMap) }

    private fun buildSortedPortals(elytraMap: ElytraMap): TreeSet<PortalDTO> = transaction {
        val portalDTOS = elytraMap.portals.map { portal: Portal ->
            PortalDTO(
                portal.locations.map { it.vector },
                portal.index, portal.map
            )
        }
        return@transaction TreeSet(portalDTOS.toSortedSet(Comparator.comparingInt(PortalDTO::index)))
    }

    private fun calculateSpline(elytraMap: ElytraMap): List<Vector3D> = transaction {
        val centerLocations = elytraMap.portals.orderBy(Portals.index to SortOrder.DESC)
            .mapNotNull { it.locations.firstOrNull { center -> center.center }?.vector } // Center Location of ech Portal
        if (centerLocations.isEmpty()) {
            return@transaction emptyList()
        }
        return@transaction centerLocations.windowed(6)
            .map { interpolate(it, 0, POINTS_PER_SEGMENT) + interpolate(it, 2, POINTS_PER_SEGMENT) }
            .reduce { acc, vector3DS -> acc + vector3DS }
    }
}

