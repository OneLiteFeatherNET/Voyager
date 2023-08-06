package net.elytrarace.commands

import cloud.commandframework.annotations.CommandMethod
import net.elytrarace.Voyager
import net.elytrarace.conversation.CupPrompt
import net.elytrarace.conversation.MapPrompt
import net.elytrarace.conversation.PortalPrompt
import org.bukkit.entity.Player

class SetupCommand(val voyager: Voyager) {

    @CommandMethod("voyager setup cup")
    fun setupCup(player: Player) {
        voyager.setupService.startSetup(player, CupPrompt())
    }

    @CommandMethod("voyager setup map")
    fun setupMap(player: Player) {
        voyager.setupService.startSetup(player, MapPrompt())
    }

    @CommandMethod("voyager setup portal")
    fun setupPortal(player: Player) {
        voyager.setupService.startSetup(player, PortalPrompt())
    }

}