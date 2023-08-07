package net.elytrarace.commands

import cloud.commandframework.annotations.CommandMethod
import cloud.commandframework.annotations.CommandPermission
import net.elytrarace.Voyager
import net.elytrarace.phases.LobbyPhase
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class MapCommand(val voyager: Voyager) {

    @CommandMethod("voyager map skip")
    @CommandPermission("voyager.command.map.skip")
    fun skipMap(player: Player) {
        val phase = voyager.elytraPhase.currentPhase
        phase.finish()
    }
}