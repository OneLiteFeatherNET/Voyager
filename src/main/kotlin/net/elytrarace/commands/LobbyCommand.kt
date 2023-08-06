package net.elytrarace.commands

import cloud.commandframework.annotations.CommandMethod
import net.elytrarace.Voyager
import net.elytrarace.conversation.cup.CupPrompt
import net.elytrarace.phases.LobbyPhase
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

class LobbyCommand(val voyager: Voyager) {

    @CommandMethod("voyager lobby force")
    fun forceStart(player: Player) {
        val phase = voyager.elytraPhase.currentPhase
        if (phase is LobbyPhase) {
            phase.currentTicks = 20
            player.sendMessage(Component.translatable("phase.lobby.force"))
        }
    }
}