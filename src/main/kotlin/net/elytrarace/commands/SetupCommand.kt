package net.elytrarace.commands

import cloud.commandframework.annotations.CommandMethod
import net.elytrarace.Voyager
import net.elytrarace.conversation.cup.CupPrompt
import net.elytrarace.conversation.map.MapPrompt
import net.elytrarace.conversation.portal.PortalPrompt
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