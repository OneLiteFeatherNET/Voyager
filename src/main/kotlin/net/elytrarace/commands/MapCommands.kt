package net.elytrarace.commands

import cloud.commandframework.annotations.Argument
import cloud.commandframework.annotations.CommandMethod
import cloud.commandframework.annotations.specifier.Quoted
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.regions.selector.RegionSelectorType
import net.elytrarace.Voyager
import net.elytrarace.models.ElytraMap
import net.elytrarace.models.ElytraMaps
import net.elytrarace.utils.PREFIX
import net.elytrarace.utils.VOID_GEN_STRING
import net.elytrarace.utils.extensions.sendFormattedMiniMessage
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path

class MapCommands(
    val voyager: Voyager
) {
    @CommandMethod("voyager map create <name> <world>")
    fun createMap(player: Player, @Argument("name") name: String, @Argument("world") world: String) = transaction {
        if (Files.exists(Path.of(world))) {
            val map = ElytraMap.new {
                this.name = name
                this.world = world
            }
            Bukkit.createWorld(WorldCreator.name(map.world).generator(VOID_GEN_STRING))
        } else {
            player.sendFormattedMiniMessage("%s<red>Map not found!", PREFIX)
        }
    }
    @CommandMethod("voyager map tp <world>")
    fun teleportMap(player: Player, @Argument("world") world: World) {
        player.teleportAsync(world.spawnLocation)
    }

    @CommandMethod("voyager map display <world> <display>")
    fun displayName(player: Player,
                    @Argument("world") world: World,
                    @Argument("display") @Quoted display: String)  {
        transaction {
            val map = ElytraMap.find { ElytraMaps.world eq world.name }.firstOrNull() ?: run {
                player.sendFormattedMiniMessage("%s<red>Map not found", PREFIX)
                return@transaction
            }
            map.displayName = display
        }
        player.sendFormattedMiniMessage("%s<green>Map display successfully updated", PREFIX)
        this.voyager.mapService.reloadActiveMaps()
    }

    @CommandMethod("voyager map author <world> <author>")
    fun author(player: Player,
                    @Argument("world") world: World,
                    @Argument("author") @Quoted author: String)  {
        transaction {
            val map = ElytraMap.find { ElytraMaps.world eq world.name }.firstOrNull() ?: run {
                player.sendFormattedMiniMessage("%s<red>Map not found", PREFIX)
                return@transaction
            }
            map.author = author
        }
        player.sendFormattedMiniMessage("%s<green>Map author successfully updated", PREFIX)
        this.voyager.mapService.reloadActiveMaps()
    }

    @CommandMethod("voyager map setup start <world>")
    fun setupStart(player: Player, @Argument("world") world: World) {
        val actor = BukkitAdapter.adapt(player)
        val localSession = actor.session
        localSession.defaultRegionSelector = RegionSelectorType.CONVEX_POLYHEDRON
        player.teleportAsync(world.spawnLocation)
        player.performCommand("//wand")
        player.sendFormattedMiniMessage("%s<yellow>You can now setup the rings", PREFIX)
    }

    @CommandMethod("voyager map setup finish <world>")
    fun setupFinish(player: Player, @Argument("world") world: World) = transaction {
        val map = ElytraMap.find { ElytraMaps.world eq world.name }.firstOrNull() ?: return@transaction
        map.setup = true
        Bukkit.getWorld("DEFAULT_WORLD")?.spawnLocation?.let { player.teleportAsync(it) }

        player.sendFormattedMiniMessage("%s<green>Map successfully setup", PREFIX)
    }
}