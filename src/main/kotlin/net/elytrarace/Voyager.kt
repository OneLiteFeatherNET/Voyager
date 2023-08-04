package net.elytrarace

import net.elytrarace.service.ConfigService
import org.bukkit.plugin.java.JavaPlugin

class Voyager : JavaPlugin() {

    val configService: ConfigService by lazy {
        ConfigService(this)
    }
    override fun onEnable() {


    }

}