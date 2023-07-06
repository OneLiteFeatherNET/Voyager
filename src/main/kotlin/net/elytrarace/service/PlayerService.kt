package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.config.PluginMode
import net.elytrarace.placeholder.GameMapSession
import net.elytrarace.placeholder.LobbyMapSession
import net.elytrarace.placeholder.PlayerSession
import net.elytrarace.utils.MAP_SELECTOR_INVENTORY_TITLE
import net.elytrarace.utils.MAP_SELECTOR_SLOT
import net.elytrarace.utils.OBJECTIVES_NAME
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.time.Duration
import java.time.Instant

class PlayerService(
    val voyager: Voyager
) {
    val playerSessions = mutableMapOf<Player, PlayerSession>()
    val lastPlayerPosition = mutableMapOf<Player, Location>()

    init {
        voyager.server.scheduler.runTaskTimerAsynchronously(voyager, showCurrentTime(), 0, 1)
    }

    private fun showCurrentTime() = Runnable {
        synchronized(playerSessions) {
            playerSessions.values.forEach {
                if (it.player.world == it.mapSession.world && it.mapSession !is LobbyMapSession && it.startTime != null) {
                    val diff = Duration.ofMillis(Instant.now().minusMillis(it.startTime.toEpochMilli()).toEpochMilli())
                    val minutes = diff.toMinutesPart().toString().padStart(2, '0')
                    val seconds = diff.toSecondsPart().toString().padStart(2, '0')
                    val millis = diff.toMillisPart().toString().dropLast(1).padStart(3, '0')

                    val currentLoc = it.player.location
                    val lastLoc = lastPlayerPosition.getOrPut(it.player) { currentLoc }
                    val distance = currentLoc.distance(lastLoc)
                    val blocksPerSeconds = (distance / 0.05).toInt()
                    it.player.sendActionBar(
                        MiniMessage.miniMessage()
                            .deserialize("<green>$minutes:$seconds:$millis - ${blocksPerSeconds}B/s")
                    )
                    lastPlayerPosition[it.player] = currentLoc
                }
            }
        }
    }

    fun joinPlayer(player: Player) {
        player.gameMode = GameMode.CREATIVE
        // Clear inventory
        player.inventory.clear()
        // Set map selector
        player.inventory.setItem(MAP_SELECTOR_SLOT, voyager.inventoryService.mapSelectorItem)
        player.inventory.heldItemSlot = MAP_SELECTOR_SLOT

        playerSessions[player] = PlayerSession(
            0,
            player,
            createMapSelectorInventory(player),
            voyager.mapService.lobbyMapSession,
            null
        )
        player.teleportAsync(voyager.mapService.lobbyMapSession.lobbyWorld.bukkitLocation)
    }

    fun openSelector(player: Player) {
        val session = playerSessions[player] ?: return
        if (voyager.inventoryService.itemsPerPage.isEmpty()) {
            player.openInventory(session.mapSelectorInventory)
            return
        }
        val items = voyager.inventoryService.itemsPerPage[session.currentPage]
        session.mapSelectorInventory.clear()
        items.map { it.displayItem }.forEachIndexed { index, itemStack ->
            session.mapSelectorInventory.setItem(index, itemStack)
        }
        player.openInventory(session.mapSelectorInventory)
    }

    fun joinMap(gameMapSession: GameMapSession, playerSession: PlayerSession) {
        playerSession.player.teleportAsync(gameMapSession.world.spawnLocation)
        playerSession.player.inventory.clear()
        playerSession.player.inventory.chestplate = voyager.inventoryService.elytraItem
        playerSession.player.inventory.setItemInOffHand(voyager.inventoryService.boostItem)
        playerSessions[playerSession.player] = playerSession.copy(mapSession = gameMapSession)
    }

    fun finishMap(playerSession: PlayerSession) {
        playerSession.player.inventory.clear()
        val sb = playerSession.player.scoreboard
        sb.getObjective(OBJECTIVES_NAME)?.unregister()
        playerSession.player.scoreboard = sb
        if (voyager.configService.config.pluginMode == PluginMode.TESTING) {
            playerSession.player.teleportAsync(playerSession.mapSession.world.spawnLocation)
            playerSessions[playerSession.player] =
                    playerSession.copy(startTime = null)
        } else {
            playerSession.player.teleportAsync(voyager.mapService.lobbyMapSession.world.spawnLocation)
            playerSession.player.inventory.setItem(MAP_SELECTOR_SLOT, voyager.inventoryService.mapSelectorItem)
            playerSession.player.inventory.heldItemSlot = MAP_SELECTOR_SLOT
            playerSessions[playerSession.player] =
                    playerSession.copy(mapSession = voyager.mapService.lobbyMapSession, startTime = null)
        }


    }

    private fun createMapSelectorInventory(player: Player): Inventory {
        return Bukkit.createInventory(
            player,
            5 * 9,
            MiniMessage.miniMessage().deserialize(MAP_SELECTOR_INVENTORY_TITLE)
        )
    }
}