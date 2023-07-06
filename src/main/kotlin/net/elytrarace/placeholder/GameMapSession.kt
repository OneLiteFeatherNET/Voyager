package net.elytrarace.placeholder

import net.elytrarace.models.ElytraMap
import net.elytrarace.models.Ring
import net.elytrarace.models.Rings
import net.elytrarace.utils.builder.itemBuilder
import net.elytrarace.utils.extensions.interpolate
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import java.text.MessageFormat
import java.util.*


class GameMapSession(
    world: World,
    val elytraMap: ElytraMap,
    val splineLocations: List<Location> = calculateSpline(elytraMap, world),
    val displayItem: ItemStack = buildDisplayItem(elytraMap),
    val sortedRings: TreeSet<Ring> = buildSorted(elytraMap)
) : MapSession(world) {
}

private fun buildSorted(elytraMap: ElytraMap): TreeSet<Ring> = transaction {
    return@transaction TreeSet(elytraMap.rings.toSortedSet(Comparator.comparingInt(Ring::index)))
}

private fun calculateSpline(elytraMap: ElytraMap, world: World): List<Location> = transaction {
    val bukkitLocs = elytraMap.rings.orderBy(Rings.index to SortOrder.DESC)
            .mapNotNull { it.locations.firstOrNull { center -> center.center }?.bukkitLocation }
    val ringLocations = (arrayListOf(world.spawnLocation) + bukkitLocs + arrayOf(world.spawnLocation)).windowed(6)
    val firstSix = (ringLocations.last() + ringLocations.take(4).reduce { acc, locations -> acc + locations }) .toMutableList()
    val first = (interpolate(firstSix, 0, 150) + interpolate(firstSix, 2, 150))
    val locs = ringLocations.map { locations ->
        interpolate(locations, 0, 150) + interpolate(locations, 2, 150)
    }
    return@transaction first + (locs.reduce { acc, lists -> acc + lists })
}

private fun buildDisplayItem(elytraMap: ElytraMap): ItemStack = transaction {
    println(elytraMap.displayName)
    return@transaction itemBuilder {
        material(Material.FILLED_MAP)
        itemFlag(arrayOf(ItemFlag.HIDE_ITEM_SPECIFICS))
        displayName(
            MiniMessage.miniMessage()
                .deserialize(MessageFormat.format("<#fcba03><!i>{0}", *arrayOf(elytraMap.displayName)))
        )
        lore(
            arrayOf(
                MiniMessage.miniMessage()
                    .deserialize(MessageFormat.format("<#03fc8c><!i>{0}", *arrayOf(elytraMap.author)))
            )
        )
    }
}