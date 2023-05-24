package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.utils.BOOST_ITEM_NAME
import net.elytrarace.utils.ELYTRA_ITEM_NAME
import net.elytrarace.utils.MAP_SELECTOR_ITEM_NAME
import net.elytrarace.utils.builder.itemBuilder
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material

class InventoryService(
    val voyager: Voyager
) {

    val mapSelectorItem = itemBuilder {
        material(Material.PAPER)
        displayName(MiniMessage.miniMessage().deserialize(MAP_SELECTOR_ITEM_NAME))
    }
    val mapSelectorPlaceHolder = itemBuilder {
        material(Material.BLACK_STAINED_GLASS_PANE)
        displayName(MiniMessage.miniMessage().deserialize(" "))
    }

    val elytraItem = itemBuilder {
        material(Material.ELYTRA)
        displayName(MiniMessage.miniMessage().deserialize(ELYTRA_ITEM_NAME))
    }

    val boostItem = itemBuilder {
        material(Material.FIREWORK_ROCKET)
        displayName(MiniMessage.miniMessage().deserialize(BOOST_ITEM_NAME))
    }

    val itemsPerPage = voyager.mapService.mapSessions.windowed(4 * 9, 1, true)

}