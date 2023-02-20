package net.elytrarace

import net.elytrarace.listener.BasicPlayerListener
import net.elytrarace.listener.BasicWorldListener
import net.elytrarace.service.CommandService
import net.elytrarace.service.ConfigService
import net.elytrarace.service.DatabaseService
import net.elytrarace.service.MapService
import org.bukkit.plugin.java.JavaPlugin

class Voyager : JavaPlugin() {

    lateinit var configService: ConfigService
    lateinit var mapService: MapService
    override fun onEnable() {
        configService = ConfigService(this)
        DatabaseService(this)
        server.pluginManager.registerEvents(BasicWorldListener(), this)
        server.pluginManager.registerEvents(BasicPlayerListener(), this)
        mapService = MapService(this)
        CommandService(this)
    }

}