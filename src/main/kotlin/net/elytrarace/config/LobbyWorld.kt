package net.elytrarace.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Location
import org.bukkit.World

@Serializable
data class LobbyWorld(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    @Transient
    val world: World? = null
) {
    val bukkitLocation: Location
        get() {
            return Location(world, x, y, z, yaw, pitch)
        }
}
