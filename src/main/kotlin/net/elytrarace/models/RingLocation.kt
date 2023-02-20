package net.elytrarace.models

import org.bukkit.Bukkit
import org.bukkit.Location
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.transactions.transaction

object RingLocations : IntIdTable() {
    val x = integer("x")
    val y = integer("y")
    val z = integer("z")
    val center = bool("center").default(false)
    val ring = reference("ring", Rings)
}

class RingLocation(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RingLocation>(RingLocations)

    var x by RingLocations.x
    var y by RingLocations.y
    var z by RingLocations.z
    var center by RingLocations.center
    var ring by Ring referencedOn RingLocations.ring

    val bukkitLocation: Location
        get() = transaction {
            return@transaction Location(Bukkit.getWorld(ring.map.world)!!, x.toDouble(), y.toDouble(), z.toDouble())
        }
}