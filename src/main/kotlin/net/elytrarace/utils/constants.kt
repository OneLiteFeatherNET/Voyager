package net.elytrarace.utils

import net.kyori.adventure.key.Key
import org.bukkit.Particle

// Chat
const val PREFIX = "<dark_gray>[</dark_gray><gradient:#fcba03:#03fc8c>ElytraRace</gradient><dark_gray>]</dark_gray> "
const val COLOR_YELLOW_STRING = "#fcba03"
const val COLOR_GREEN_STRING = "#03fc8c"

val NAMESPACED_KEY: Key = Key.key("voyager", "i18n")


// Config
const val CONFIG_FILE_NAME = "config.conf"

// Ring
const val MINIMUM_POINT = 3

// World
const val VOID_GEN_STRING = "VoidGen"
const val LOBBY_WORLD_FILE_NAME = "lobby.json"

// Scheduler
const val SHOW_LINE_DELAY = 0L
const val SHOW_LINE_TIMER = 10L
const val SHOW_LINE_OFFSET = 0.0
const val SHOW_LINE_COUNT = 1
const val SHOW_LINE_EXTRA = .0
val SHOW_LINE_PARTICLE = Particle.FLAME
val SHOW_DEBUG_LINE_PARTICLE = Particle.DRIP_WATER
val SHOW_DEBUG_LOC_LINE_PARTICLE = Particle.CLOUD
val SHOW_DEBUG_INTERSECTION_LINE_PARTICLE = Particle.SCULK_SOUL

// Inventory
const val MAP_SELECTOR_SLOT = 4
const val MAP_SELECTOR_ITEM_NAME = "<gradient:#347deb:#eb346b>Map Selector</gradient>"
const val ELYTRA_ITEM_NAME = "<gradient:#347deb:#eb346b>Elytra</gradient>"
const val BOOST_ITEM_NAME = "<gradient:#347deb:#eb346b>BOOST</gradient>"
const val MAP_SELECTOR_INVENTORY_TITLE = "<gradient:#347deb:#eb346b>Map Selector</gradient>"

// Scoreboard
const val OBJECTIVES_NAME = "timings"