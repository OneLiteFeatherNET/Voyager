package net.elytrarace.models

import net.elytrarace.models.RingLocation.Companion.referrersOn
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

object Maps : IntIdTable() {
    val name = varchar("name", 32)
    val world = varchar("world", 32)
    val displayName = varchar("displayName", 32)
    val author = varchar("author", 32)
}

class Map(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Map>(Maps)
    val name by Maps.name
    val world by Maps.world
    val displayName by Maps.displayName
    val author by Maps.author
    val rings by Ring referrersOn Rings.map
}