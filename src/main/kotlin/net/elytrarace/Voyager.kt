package net.elytrarace

import net.elytrarace.listener.BasicWorldListener
import net.elytrarace.model.config.CupConfiguration
import net.elytrarace.model.dbo.Cup
import net.elytrarace.model.dbo.Cups
import net.elytrarace.model.dto.GameMapSession
import net.elytrarace.phase.LinearPhaseSeries
import net.elytrarace.phase.Phase
import net.elytrarace.phases.GamePhase
import net.elytrarace.phases.LobbyPhase
import net.elytrarace.service.ConfigService
import net.elytrarace.service.DatabaseService
import net.elytrarace.utils.LynxWrapper
import net.kyori.adventure.key.Key
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationRegistry
import net.kyori.adventure.util.UTF8ResourceBundleControl
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class Voyager : JavaPlugin() {

    private val supportedLocals: Array<Locale> = arrayOf(Locale.US)

    val configService: ConfigService by lazy {
        ConfigService(this)
    }
    val databaseService: DatabaseService by lazy {
        DatabaseService(this)
    }
    val cup: Cup? by lazy {
        getCup(configService.config.cupConfiguration)
    }

    val elytraPhase = LinearPhaseSeries<Phase>()
    val playableMaps: MutableList<GameMapSession> = mutableListOf()

    private fun getCup(cupConfiguration: CupConfiguration): Cup? = transaction {
        val rowResult = Cups.select { Cups.name eq cupConfiguration.cupName }.firstOrNull() ?: return@transaction null
        return@transaction Cup.wrapRow(rowResult)
    }

    override fun onEnable() {

        val registry = TranslationRegistry.create(Key.key("voyager", "localization"))
        supportedLocals.forEach { locale ->
            val bundle = ResourceBundle.getBundle("voyager", locale, UTF8ResourceBundleControl.get())
            registry.registerAll(locale, bundle, false)
        }
        registry.defaultLocale(supportedLocals.first())
        GlobalTranslator.translator().addSource(LynxWrapper(registry))
        databaseService

        server.pluginManager.registerEvents(BasicWorldListener(), this)
        if (this.cup != null) {
            elytraPhase.add(LobbyPhase(this, cupConfiguration = configService.config.cupConfiguration))
            cup?.maps?.forEach {
                val world = Bukkit.createWorld(WorldCreator.name(it.name).generator("VoidGen").type(WorldType.NORMAL))
                if (world != null) {
                    val gameMapSession = GameMapSession(world, it)
                    playableMaps.add(gameMapSession)
                    elytraPhase.add(GamePhase(this, gameMapSession))
                }


            }
            elytraPhase.start()
        }


    }

}