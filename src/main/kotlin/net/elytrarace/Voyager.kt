package net.elytrarace

import net.elytrarace.listener.BasicListener
import net.elytrarace.listener.RaceListener
import net.elytrarace.listener.SetupListener
import net.elytrarace.model.config.CupConfiguration
import net.elytrarace.model.dbo.Cup
import net.elytrarace.model.dbo.Cups
import net.elytrarace.model.dto.GameMapSession
import net.elytrarace.phase.LinearPhaseSeries
import net.elytrarace.phase.Phase
import net.elytrarace.phases.EndPhase
import net.elytrarace.phases.GamePhase
import net.elytrarace.phases.LobbyPhase
import net.elytrarace.service.*
import net.elytrarace.utils.LynxWrapper
import net.elytrarace.utils.OBJECTIVES_NAME
import net.elytrarace.utils.TOP_THREE_OBJECTIVES_NAME
import net.kyori.adventure.key.Key
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationRegistry
import net.kyori.adventure.util.UTF8ResourceBundleControl
import org.bukkit.Bukkit
import org.bukkit.GameRule
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

    val commandService: CommandService by lazy {
        CommandService(this)
    }

    val setupService: SetupService by lazy {
        SetupService(this)
    }

    val playerService: PlayerService by lazy {
        PlayerService(this)
    }

    val detectionService: PortalDetectionService by lazy {
        PortalDetectionService()
    }

    val inventoryService: InventoryService by lazy {
        InventoryService(this)
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
        Bukkit.getScoreboardManager().mainScoreboard.getObjective(OBJECTIVES_NAME)?.unregister()
        Bukkit.getScoreboardManager().mainScoreboard.getObjective(TOP_THREE_OBJECTIVES_NAME)?.unregister()
        registry.defaultLocale(supportedLocals.first())
        GlobalTranslator.translator().addSource(LynxWrapper(registry))
        databaseService

        server.pluginManager.registerEvents(BasicListener(this), this)
        server.pluginManager.registerEvents(SetupListener(this), this)
        server.pluginManager.registerEvents(RaceListener(this), this)
        this.cup
        if (this.cup != null) {
            elytraPhase.add(LobbyPhase(this, cupConfiguration = configService.config.cupConfiguration))
            transaction {
                cup?.maps?.forEach {
                    val world = Bukkit.createWorld(WorldCreator.name(it.world).generator("VoidGen").type(WorldType.NORMAL))
                    if (world != null) {
                        world.setGameRule(GameRule.DO_INSOMNIA, false)
                        val gameMapSession = GameMapSession(world, it)
                        playableMaps.add(gameMapSession)
                        elytraPhase.add(GamePhase(this@Voyager, gameMapSession))
                    }


                }
            }
            elytraPhase.add(EndPhase(this))

            elytraPhase.start()
        }
        this.commandService.registerCommands()


    }

}