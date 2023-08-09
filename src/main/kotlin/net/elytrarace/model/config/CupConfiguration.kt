package net.elytrarace.model.config

import com.zaxxer.hikari.HikariConfig
import kotlinx.serialization.Serializable

@Serializable
data class CupConfiguration(
    val cupName: String,
    val playerSize: Int,
    val minPlayerSize: Int,
    val coolDown: Int = 10,
    val boostLevel: Int = 2,
    val cupPointsPerMap: Int = 100
)
