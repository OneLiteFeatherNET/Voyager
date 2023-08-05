package net.elytrarace.model.dbo

import net.elytrarace.model.dbo.Portals.nullable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable


object ElytraMaps : IntIdTable() {
    val name = varchar("name", 32)
    val world = varchar("world", 32)
    val displayName = varchar("displayName",32).nullable()
    val author = varchar("author",32).nullable()
    val cup = reference("cup", Cups).nullable().index()
}
class ElytraMap(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<ElytraMap>(ElytraMaps)
    var name by ElytraMaps.name
    var world by ElytraMaps.world
    var displayName by ElytraMaps.displayName
    var author by ElytraMaps.author
    var cup by Cup optionalReferencedOn ElytraMaps.cup
    val portals by Portal referrersOn Portals.map
}