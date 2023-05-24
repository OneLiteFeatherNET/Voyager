package net.elytrarace

import net.elytrarace.listener.BasicPlayerListener
import net.elytrarace.listener.BasicWorldListener
import net.elytrarace.listener.MapSelectorListener
import net.elytrarace.listener.RaceBasicListener
import net.elytrarace.service.*
import net.elytrarace.utils.NAMESPACED_KEY
import net.kyori.adventure.translation.TranslationRegistry
import org.bukkit.plugin.java.JavaPlugin

class Voyager : JavaPlugin() {

    lateinit var configService: ConfigService
    lateinit var mapService: MapService
    lateinit var inventoryService: InventoryService
    lateinit var playerService: PlayerService
    val translationRegistry: TranslationRegistry = TranslationRegistry.create(NAMESPACED_KEY)
    override fun onEnable() {
        configService = ConfigService(this)
        DatabaseService(this)
        mapService = MapService(this)
        inventoryService = InventoryService(this)
        playerService = PlayerService(this)
        server.pluginManager.registerEvents(BasicWorldListener(), this)
        server.pluginManager.registerEvents(BasicPlayerListener(this), this)
        server.pluginManager.registerEvents(MapSelectorListener(this), this)
        server.pluginManager.registerEvents(RaceBasicListener(this), this)
        CommandService(this)
        /*val bundle = ResourceBundle.getBundle("test", Locale.US)
        translationRegistry.registerAll(Locale.US, bundle.keySet()) {
            val format = bundle.getString(it)
            val alreadyDezelized = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(TranslationRegistry.SINGLE_QUOTE_PATTERN.matcher(format).replaceAll("''")))
            println(alreadyDezelized)
            return@registerAll MessageFormat(
                alreadyDezelized,
                Locale.US
            )
        }
        GlobalTranslator.translator().addSource(translationRegistry)*/

    }

}