package net.elytrarace.listener

import net.elytrarace.Voyager
import net.elytrarace.phases.EndPhase
import net.elytrarace.phases.GamePhase
import net.elytrarace.utils.api.CancellableListener
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.data.type.Chest
import org.bukkit.block.data.type.Farmland
import org.bukkit.block.data.type.Sign
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
import org.bukkit.event.server.ServerListPingEvent

class BasicListener(
    val voyager: Voyager
) : Listener, CancellableListener {
    @EventHandler
    fun leafDecay(event: LeavesDecayEvent) = cancelling(event)

    @EventHandler
    fun armorStandManipulateEvent(event: PlayerArmorStandManipulateEvent) = cancelling(event)

    @EventHandler
    fun bedEnterEvent(event: PlayerBedEnterEvent) = cancelling(event)

    @EventHandler
    fun blockBreak(event: BlockBreakEvent) = cancelling(event)

    @EventHandler
    fun blockPlace(event: BlockPlaceEvent) = cancelling(event)

    @EventHandler
    fun playerInteractEvent(event: PlayerInteractEvent) {
        if (event.action == Action.PHYSICAL && event.clickedBlock != null && event.clickedBlock?.blockData is Farmland) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.blockData is Sign) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.blockData is Chest) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.CRAFTING_TABLE) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.FURNACE) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.ENCHANTING_TABLE) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.SMOKER) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.BLAST_FURNACE) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.CARTOGRAPHY_TABLE) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.ENDER_CHEST) {
            cancelling(event)
            return
        }
        if (event.action == Action.RIGHT_CLICK_BLOCK && event.clickedBlock != null && event.clickedBlock?.type == Material.GLOW_BERRIES) {
            cancelling(event)
            return
        }
    }

    @EventHandler
    fun handlePing(event: ServerListPingEvent) {
        voyager.cup ?: return
        event.motd(
            Component.translatable("motd.cup")
                .args(MiniMessage.miniMessage().deserialize(voyager.cup?.displayName ?: ""))
        )
        event.maxPlayers = voyager.configService.config.cupConfiguration.playerSize
    }

    @EventHandler
    fun handleDrop(event: PlayerDropItemEvent) = cancelling(event)

    @EventHandler
    fun handleInventory(event: InventoryClickEvent) {
        cancelling(event)
        event.result = Event.Result.DENY
    }

    @EventHandler
    fun handlePickup(event: EntityPickupItemEvent) = cancelling(event)

    @EventHandler
    fun handleDamage(event: EntityDamageEvent) = cancelling(event)

    @EventHandler
    fun handleFood(event: FoodLevelChangeEvent) = cancelling(event)

    @EventHandler
    fun handlePlayerLogin(event: PlayerLoginEvent) {
        if (this.voyager.elytraPhase.currentPhase is GamePhase || this.voyager.elytraPhase.currentPhase is EndPhase) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.empty())
        }
    }

    @EventHandler
    fun handlePlayerJoinEvent(event: PlayerJoinEvent) {
        event.player.teleportAsync(this.voyager.configService.config.lobbyConfiguration.bukkitLocation)
        event.player.closeInventory()
        event.player.inventory.clear()
        event.player.foodLevel = 20
        event.player.health = 20.0
        event.player.gameMode = GameMode.SURVIVAL
        if (event.player.scoreboard == Bukkit.getScoreboardManager().mainScoreboard) {
            event.player.scoreboard = Bukkit.getScoreboardManager().newScoreboard
        }
        this.voyager.playerService.handlePlayerJoinLobbyPhase(player = event.player)
    }
}