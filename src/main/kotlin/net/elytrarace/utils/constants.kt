package net.elytrarace.utils

import org.apache.commons.numbers.core.Precision
import org.bukkit.Particle

// Chat
const val PREFIX = "<dark_gray>[</dark_gray><gradient:#fcba03:#03fc8c>ElytraRace</gradient><dark_gray>]</dark_gray> "
const val COLOR_YELLOW_STRING = "#fcba03"
const val COLOR_GREEN_STRING = "#03fc8c"

// Config
const val CONFIG_FILE_NAME = "config.conf"

// Portal
const val MINIMUM_POINT = 3
val precision: Precision.DoubleEquivalence = Precision.doubleEquivalenceOfEpsilon(1e-6)

// World
const val VOID_GEN_STRING = "VoidGen"
const val LOBBY_WORLD_FILE_NAME = "lobby.json"

const val POINTS_PER_SEGMENT = 75
val SHOW_LINE_PARTICLE = Particle.FLAME
const val SHOW_LINE_OFFSET = 0.0
const val SHOW_LINE_COUNT = 1
const val SHOW_LINE_EXTRA = .0

// Items
const val ELYTRA_ITEM_NAME = "<gradient:#347deb:#eb346b>Elytra</gradient>"
const val BOOST_ITEM_NAME = "<gradient:#347deb:#eb346b>BOOST</gradient>"
// Scoreboard
const val OBJECTIVES_NAME = "timings"
const val CUP_OBJECTIVES_NAME = "cup"
const val TOP_THREE_OBJECTIVES_NAME = "topThree"