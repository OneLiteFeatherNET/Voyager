package net.elytrarace.service

import com.typesafe.config.ConfigFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import kotlinx.serialization.json.Json
import net.elytrarace.Voyager
import net.elytrarace.config.LobbyWorld
import net.elytrarace.config.SQLConfig
import net.elytrarace.utils.CONFIG_FILE_NAME
import net.elytrarace.utils.LOBBY_WORLD_FILE_NAME
import java.nio.file.Files
import kotlin.io.path.reader

class ConfigService(private val voyager: Voyager) {
    val config: SQLConfig
        get() = readConfig(voyager)
    val lobbyWorld: LobbyWorld?
        get() = readLobbyWorld(voyager)

    private fun readLobbyWorld(voyager: Voyager): LobbyWorld? {
        val world = voyager.server.worlds.firstOrNull {
            val jsonFile = it.worldFolder.resolve(LOBBY_WORLD_FILE_NAME)
            Files.exists(jsonFile.toPath())
        } ?: return null
        val jsonFile = world.worldFolder.resolve(LOBBY_WORLD_FILE_NAME)
        return Json.decodeFromString<LobbyWorld?>(jsonFile.readText())?.copy(world = world)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readConfig(voyager: Voyager): SQLConfig {
        val sqlConfigFile = voyager.dataFolder.toPath().resolve(CONFIG_FILE_NAME)
        if (!Files.exists(sqlConfigFile)) {
            voyager.saveResource(CONFIG_FILE_NAME, true)
        }
        sqlConfigFile.reader().use { t ->
            val config = ConfigFactory.parseReader(t)
            return Hocon.decodeFromConfig(config)
        }
    }
}