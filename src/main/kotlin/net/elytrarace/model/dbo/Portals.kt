package net.elytrarace.model.dbo

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Portals : IntIdTable() {
    val index = integer("index")
    val map = reference("map", ElytraMaps).index()
}
class Portal(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Portal>(Portals)

    var index by Portals.index
    var map by ElytraMap referencedOn Portals.map
    val locations by PortalLocation referrersOn PortalLocations.portal


}