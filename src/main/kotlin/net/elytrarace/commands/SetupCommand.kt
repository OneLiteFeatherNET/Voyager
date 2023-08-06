package net.elytrarace.commands

import cloud.commandframework.annotations.CommandMethod
import net.elytrarace.Voyager
import org.bukkit.entity.Player

class SetupCommand(val voyager: Voyager) {

    @CommandMethod("voyager setup")
    fun setup(player: Player) {
        voyager.setupService.startSetup(player)
    }

}