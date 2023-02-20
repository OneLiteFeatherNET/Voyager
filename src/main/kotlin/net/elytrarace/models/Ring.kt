package net.elytrarace.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Rings : IntIdTable() {
    val index = integer("index")
    val map = reference("map", ElytraMaps)
}

class Ring(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Ring>(Rings)

    var index by Rings.index
    var map by ElytraMap referencedOn Rings.map
    val locations by RingLocation referrersOn RingLocations.ring


}