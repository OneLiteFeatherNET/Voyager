package net.elytrarace.model.dbo

import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.transactions.transaction

object PortalLocations : IntIdTable() {
    val x = integer("x")
    val y = integer("y")
    val z = integer("z")
    val center = bool("center").default(false)
    val portal = reference("portal", Portals).index()

}

class PortalLocation(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PortalLocation>(PortalLocations)

    var x by PortalLocations.x
    var y by PortalLocations.y
    var z by PortalLocations.z
    var center by PortalLocations.center
    var portal by Portal referencedOn PortalLocations.portal

    val vector: Vector3D
        get() = transaction {
            return@transaction Vector3D.of(x.toDouble(), y.toDouble(), z.toDouble())
        }

}