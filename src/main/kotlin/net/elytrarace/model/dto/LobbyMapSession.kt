package net.elytrarace.model.dto

import net.elytrarace.model.config.LobbyConfig
import org.bukkit.World

class LobbyMapSession(world: World, val lobbyConfig: LobbyConfig) : MapSession(world) {
}