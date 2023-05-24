package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.placeholder.GameMapSession
import net.elytrarace.placeholder.LobbyMapSession
import net.elytrarace.placeholder.PlayerSession
import net.elytrarace.utils.MAP_SELECTOR_INVENTORY_TITLE
import net.elytrarace.utils.MAP_SELECTOR_SLOT
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.time.Duration
import java.time.Instant

class PlayerService(
    val voyager: Voyager
) {
    val playerSessions = mutableMapOf<Player, PlayerSession>()

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
                    it.player.sendActionBar(MiniMessage.miniMessage().deserialize("<green>$minutes:$seconds:$millis"))
                }
            }
        }
    }

    fun joinPlayer(player: Player) {
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

    private fun createMapSelectorInventory(player: Player): Inventory {
        return Bukkit.createInventory(
            player,
            5 * 9,
            MiniMessage.miniMessage().deserialize(MAP_SELECTOR_INVENTORY_TITLE)
        )
    }
}