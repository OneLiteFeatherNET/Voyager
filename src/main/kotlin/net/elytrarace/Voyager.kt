package net.elytrarace

import net.elytrarace.listener.BasicPlayerListener
import net.elytrarace.listener.BasicWorldListener
import net.elytrarace.listener.MapSelectorListener
import net.elytrarace.service.CommandService
import net.elytrarace.service.ConfigService
import net.elytrarace.service.DatabaseService
import net.elytrarace.service.InventoryService
import net.elytrarace.service.MapService
import org.bukkit.plugin.java.JavaPlugin

class Voyager : JavaPlugin() {

    lateinit var configService: ConfigService
    lateinit var mapService: MapService
    lateinit var inventoryService: InventoryService
    override fun onEnable() {
        inventoryService = InventoryService()
        configService = ConfigService(this)
        DatabaseService(this)
        server.pluginManager.registerEvents(BasicWorldListener(), this)
        server.pluginManager.registerEvents(BasicPlayerListener(this), this)
        server.pluginManager.registerEvents(MapSelectorListener(this), this)
        mapService = MapService(this)
        CommandService(this)
    }

}