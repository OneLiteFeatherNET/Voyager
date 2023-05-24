package net.elytrarace.commands


import cloud.commandframework.annotations.CommandMethod
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.elytrarace.config.LobbyWorld
import net.elytrarace.utils.LOBBY_WORLD_FILE_NAME
import net.elytrarace.utils.PREFIX
import net.elytrarace.utils.extensions.sendFormattedMiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files

class VoyagerCommands {

    @CommandMethod("voyager lobby set")
    fun setLobbySpawn(player: Player) = transaction {
        val worlds = Bukkit.getWorlds().filter {
            val jsonFile = it.worldFolder.resolve(LOBBY_WORLD_FILE_NAME)
            Files.exists(jsonFile.toPath())
        }
        worlds.forEach {
            val jsonFile = it.worldFolder.resolve(LOBBY_WORLD_FILE_NAME)
            jsonFile.delete()
        }
        val jsonFile = player.world.worldFolder.resolve(LOBBY_WORLD_FILE_NAME)
        val rawContent = LobbyWorld(
            player.location.x,
            player.location.y,
            player.location.z,
            player.location.yaw,
            player.location.pitch,
        )
        val content = Json.encodeToString(rawContent)
        jsonFile.writeText(content)
        player.sendFormattedMiniMessage("%s<green>Lobby successfully set", PREFIX)

    }

}