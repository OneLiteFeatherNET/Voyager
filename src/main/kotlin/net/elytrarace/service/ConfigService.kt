package net.elytrarace.service

import com.typesafe.config.ConfigFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import net.elytrarace.Voyager
import net.elytrarace.config.SQLConfig
import net.elytrarace.utils.CONFIG_FILE_NAME
import java.nio.file.Files
import kotlin.io.path.reader

class ConfigService(private val voyager: Voyager) {
    val config: SQLConfig
        get() = readConfig(voyager)

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