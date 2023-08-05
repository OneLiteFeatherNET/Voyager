package net.elytrarace.model.dto

import net.elytrarace.model.dbo.ElytraMap
import net.elytrarace.model.dbo.Portal
import org.bukkit.World
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class GameMapSession(world: World, val elytraMap: ElytraMap, val sortedPortals: TreeSet<PortalDTO> = buildSortedPortals(elytraMap)) : MapSession(world) {
}

fun buildSortedPortals(elytraMap: ElytraMap): TreeSet<PortalDTO> = transaction {
    val portalDTOS = elytraMap.portals.map { portal: Portal ->
        PortalDTO(portal.locations.map { it.vector },
            portal.index, portal.map)
    }
    return@transaction TreeSet(portalDTOS.toSortedSet(Comparator.comparingInt(PortalDTO::index)))
}
