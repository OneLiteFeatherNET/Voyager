package net.elytrarace

import net.elytrarace.listener.BasicWorldListener
import net.elytrarace.service.ConfigService
import net.elytrarace.service.DatabaseService
import org.bukkit.plugin.java.JavaPlugin

class Voyager : JavaPlugin() {

    val configService: ConfigService by lazy {
        ConfigService(this)
    }
    val databaseService: DatabaseService by lazy {
        DatabaseService(this)
    }
    override fun onEnable() {
        server.pluginManager.registerEvents(BasicWorldListener(), this)
    }

}