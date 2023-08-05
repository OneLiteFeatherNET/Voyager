package net.elytrarace

import net.elytrarace.listener.BasicWorldListener
import net.elytrarace.phase.LinearPhaseSeries
import net.elytrarace.phase.Phase
import net.elytrarace.phases.LobbyPhase
import net.elytrarace.service.ConfigService
import net.elytrarace.service.DatabaseService
import net.elytrarace.utils.LynxWrapper
import net.kyori.adventure.key.Key
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationRegistry
import net.kyori.adventure.util.UTF8ResourceBundleControl
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Voyager : JavaPlugin() {

    private val supportedLocals: Array<Locale> = arrayOf(Locale.US)

    val configService: ConfigService by lazy {
        ConfigService(this)
    }
    val databaseService: DatabaseService by lazy {
        DatabaseService(this)
    }
    override fun onEnable() {

        val registry = TranslationRegistry.create(Key.key("voyager", "localization"))
        supportedLocals.forEach { locale ->
            val bundle = ResourceBundle.getBundle("voyager", locale, UTF8ResourceBundleControl.get())
            registry.registerAll(locale, bundle, false)
        }
        registry.defaultLocale(supportedLocals.first())
        GlobalTranslator.translator().addSource(LynxWrapper(registry))

        server.pluginManager.registerEvents(BasicWorldListener(), this)
        val linearPhase = LinearPhaseSeries<Phase>()
        linearPhase.add(LobbyPhase(this, cupConfiguration = configService.config.cupConfiguration))
        linearPhase.start()
    }

}