package net.elytrarace.service

import cloud.commandframework.annotations.AnnotationParser
import cloud.commandframework.arguments.parser.ParserParameters
import cloud.commandframework.arguments.parser.StandardParameters
import cloud.commandframework.bukkit.CloudBukkitCapabilities
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.meta.CommandMeta
import cloud.commandframework.paper.PaperCommandManager
import net.elytrarace.Voyager
import net.elytrarace.commands.MapCommands
import net.elytrarace.commands.RingCommands
import net.elytrarace.commands.VoyagerCommands
import net.elytrarace.config.LobbyWorld
import net.elytrarace.config.PluginMode
import org.bukkit.command.CommandSender
import java.util.function.Function

class CommandService(private val voyager: Voyager) {
    private val paperCommandManager: PaperCommandManager<CommandSender> = PaperCommandManager(
        voyager,
        CommandExecutionCoordinator.simpleCoordinator(),
        Function.identity(),
        Function.identity()
    )
    private val annotationParser: AnnotationParser<CommandSender>

    init {
        if (paperCommandManager.hasCapability(CloudBukkitCapabilities.BRIGADIER)) {
            paperCommandManager.registerBrigadier()
            voyager.getLogger().info("Brigadier support enabled")
        }
        if (paperCommandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            paperCommandManager.registerAsynchronousCompletions()
            voyager.getLogger().info("Asynchronous completions enabled")
        }
        val commandMetaFunction =
            Function<ParserParameters, CommandMeta> { p: ParserParameters ->
                CommandMeta.simple().with(
                    CommandMeta.DESCRIPTION,
                    p.get(StandardParameters.DESCRIPTION, "No description")
                ).build()
            }
        annotationParser = AnnotationParser(
            paperCommandManager,
            CommandSender::class.java, commandMetaFunction
        )
        registerCommands()
    }

    private fun registerCommands() {
        if (this.voyager.configService.config.pluginMode == PluginMode.TESTING) {
            this.annotationParser.parse(MapCommands(voyager))
            this.annotationParser.parse(RingCommands())
            this.annotationParser.parse(VoyagerCommands())
        }
    }

}