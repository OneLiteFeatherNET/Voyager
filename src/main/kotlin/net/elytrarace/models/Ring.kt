package net.elytrarace.models

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

object Rings : IntIdTable() {
    val index = integer("index")
    val map = reference("map", Maps)
}

class Ring(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Ring>(Rings)

    val map by Map referencedOn Rings.map
    val locations by RingLocation referrersOn RingLocations.ring
}