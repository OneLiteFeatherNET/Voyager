package net.elytrarace.service

import com.zaxxer.hikari.HikariDataSource
import net.elytrarace.Voyager
import net.elytrarace.logger.Voyager2SQLLogger
import net.elytrarace.models.ElytraMap
import net.elytrarace.models.ElytraMaps
import net.elytrarace.models.RingLocations
import net.elytrarace.models.Rings
import net.elytrarace.utils.VOID_GEN_STRING
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

class DatabaseService(private val voyager: Voyager) {
    init {
        val db = Database.connect(
            HikariDataSource(voyager.configService.config.toHikariConfig()),
            databaseConfig = DatabaseConfig {
                sqlLogger = Voyager2SQLLogger(voyager.logger)
            }
        )
        TransactionManager.defaultDatabase = db
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                ElytraMaps,
                Rings,
                RingLocations
            )
        }
        transaction {
            ElytraMap.all().forEach {
                Bukkit.createWorld(WorldCreator.name(it.world).generator(VOID_GEN_STRING))
            }
        }
    }
}