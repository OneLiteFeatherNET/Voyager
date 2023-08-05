package net.elytrarace.model.dbo

import net.elytrarace.model.dbo.ElytraMaps.nullable
import net.elytrarace.model.dbo.Portal.Companion.referrersOn
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

object Cups : IntIdTable() {
    val name = varchar("name", 32)
    val displayName = varchar("displayName",32).nullable()
}

class Cup(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Cup>(Cups)
    var name by Cups.name
    var displayName by Cups.displayName
    val maps by Portal optionalReferrersOn  Portals.cup
}