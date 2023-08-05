package net.elytrarace.model.dto

import net.elytrarace.model.config.LobbyConfiguration
import org.bukkit.World

class LobbyMapSession(world: World, val lobbyConfiguration: LobbyConfiguration) : MapSession(world) {
}