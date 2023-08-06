package net.elytrarace.service

import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.utils.BOOST_ITEM_NAME
import net.elytrarace.utils.ELYTRA_ITEM_NAME
import net.elytrarace.utils.builder.itemBuilder
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerItemDamageEvent

class InventoryService {

    val elytraItem = itemBuilder {
        material(Material.ELYTRA)
            unbreakable()
        displayName(MiniMessage.miniMessage().deserialize(ELYTRA_ITEM_NAME))
    }

    val boostItem = itemBuilder {
        material(Material.FIREWORK_ROCKET)
        unbreakable()
        displayName(MiniMessage.miniMessage().deserialize(BOOST_ITEM_NAME))
    }

    fun handlePlayerStart(elytraPlayer: ElytraPlayer) {
        elytraPlayer.player.inventory.clear()
        elytraPlayer.player.inventory.chestplate = elytraItem
        elytraPlayer.player.inventory.setItemInOffHand(boostItem)
    }

    fun handleItemDamageEvent(event: PlayerItemDamageEvent) {
        if (event.item == elytraItem) {
            event.isCancelled = true
        }
        if (event.item == boostItem) {
            event.isCancelled = true
            event.player.boostElytra(boostItem.clone())
        }
    }

    fun handleItemConsume(event: PlayerItemConsumeEvent) {
        if (event.item == boostItem) {
            event.isCancelled = true
            event.player.boostElytra(boostItem.clone())
        }
    }

    fun handlePlayerInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        if (item == boostItem && event.player.isGliding) {
            event.isCancelled = true
            event.player.boostElytra(boostItem.clone())
        }
    }
}