package net.elytrarace.commands

import cloud.commandframework.annotations.Argument
import cloud.commandframework.annotations.CommandMethod
import cloud.commandframework.annotations.parsers.Parser
import cloud.commandframework.context.CommandContext
import com.fastasyncworldedit.core.regions.PolyhedralRegion
import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.typesafe.config.ConfigException.Null
import net.elytrarace.models.ElytraMap
import net.elytrarace.models.ElytraMaps
import net.elytrarace.models.Ring
import net.elytrarace.models.RingLocation
import net.elytrarace.models.RingLocations
import net.elytrarace.models.Rings
import net.elytrarace.utils.COLOR_GREEN_STRING
import net.elytrarace.utils.MINIMUM_POINT
import net.elytrarace.utils.PREFIX
import net.elytrarace.utils.extensions.sendFormattedMiniMessage
import org.bukkit.Location
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class RingCommands {

    @CommandMethod("voyager ring list")
    fun listRings(player: Player) = transaction {
        val map = ElytraMap.find { ElytraMaps.world eq player.world.name }.firstOrNull() ?: kotlin.run {
            player.sendFormattedMiniMessage("%s<yellow>You are not in a elytra map", PREFIX)
            return@transaction
        }
        player.sendFormattedMiniMessage("%s<$COLOR_GREEN_STRING>All rings inside of this map", PREFIX)
        map.rings.sortedBy { it.index }.forEach {
            player.sendFormattedMiniMessage(
                "<click:run_command:/voyager ring tp %d><$COLOR_GREEN_STRING>Ring(%d)</click>",
                it.index,
                it.index
            )
        }
    }

    @CommandMethod("voyager ring add <index>")
    fun addRingToMap(player: Player, @Argument("index") index: Int) {
        val actor = BukkitAdapter.adapt(player)
        val localSession = actor.session
        val selectionWorld = localSession.selectionWorld ?: throw IncompleteRegionException()
        val region = localSession.getSelection(selectionWorld)
        if (region is PolyhedralRegion) {
            if (region.vertices.size < MINIMUM_POINT) {
                player.sendFormattedMiniMessage("%s<yellow> Please use poly selection from FastAsyncWorldEdit")
                return
            }
            transaction {
                val map = ElytraMap.find { ElytraMaps.world eq player.world.name }.firstOrNull() ?: kotlin.run {
                    player.sendFormattedMiniMessage("%s<yellow>You are not in a elytra map", PREFIX)
                    return@transaction
                }
                if (Ring.find { Rings.index eq index }.any()) {
                    player.sendFormattedMiniMessage("%s<yellow>This index is already existing", PREFIX)
                    return@transaction
                }
                val ring = Ring.new {
                    this.index = index
                    this.map = map
                }
                region.vertices.forEach {
                    RingLocation.new {
                        x = it.x
                        y = it.y
                        z = it.z
                        this.ring = ring
                    }
                }
                RingLocation.new {
                    x = region.center.blockX
                    y = region.center.blockY
                    z = region.center.blockZ
                    center = true
                    this.ring = ring
                }
                player.sendFormattedMiniMessage("%s<green>Ring($index) successfully setup", PREFIX)
            }
        } else {
            player.sendFormattedMiniMessage("%s<yellow>Please use poly selection from FastAsyncWorldEdit", PREFIX)
        }
    }

    @CommandMethod("voyager ring delete <index>")
    fun deleteRing(player: Player, @Argument("index", parserName = "ringIndex") ring: Ring) = transaction {
        val locations = RingLocation.wrapRows(RingLocations.select { RingLocations.ring eq ring.id })
        locations.forEach { it.delete() }
        ring.delete()
        player.sendFormattedMiniMessage("%s<green>This ring is now deleted", PREFIX)
    }

    @CommandMethod("voyager ring tp <index>")
    fun teleportToRing(player: Player, @Argument("index", parserName = "ringIndex") ring: Ring) = transaction {
        val ringSel = PolyhedralRegion(BukkitAdapter.adapt(player.world))
        ring.locations.forEach {
            ringSel.addVertex(BlockVector3.at(it.x, it.y, it.z))
        }
        val location = Location(player.world, ringSel.center.x, ringSel.center.y, ringSel.center.z)
        player.teleportAsync(location)
        player.sendFormattedMiniMessage("%s<green>You got teleport to Ring ${ring.index}", PREFIX)
    }

    @Parser(name = "ringIndex")
    fun ringIndex(sender: CommandContext<Player>, input: Queue<String>):Ring {
        val rawInput = input.remove()
        val parsedIndex = rawInput.toIntOrNull() ?: throw IllegalArgumentException("Input is not a number")
        return Ring.wrapRow(Rings.select { Rings.index eq parsedIndex }.firstOrNull() ?: throw NullPointerException("No ring found"))
    }

}