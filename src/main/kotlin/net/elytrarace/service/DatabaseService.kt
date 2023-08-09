package net.elytrarace.service

import com.zaxxer.hikari.HikariDataSource
import net.elytrarace.Voyager
import net.elytrarace.logger.Voyager2SQLLogger
import net.elytrarace.model.dbo.Cups
import net.elytrarace.model.dbo.ElytraMaps
import net.elytrarace.model.dbo.PortalLocations
import net.elytrarace.model.dbo.Portals
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

class DatabaseService(private val voyager: Voyager) {
    init {
        val db = Database.connect(
            HikariDataSource(voyager.configService.config.sqlConfig.toHikariConfig()),
            databaseConfig = DatabaseConfig {
                sqlLogger = Voyager2SQLLogger(voyager.getLogger())
            }
        )
        TransactionManager.defaultDatabase = db
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Cups,
                ElytraMaps,
                Portals,
                PortalLocations
            )
        }
    }
}