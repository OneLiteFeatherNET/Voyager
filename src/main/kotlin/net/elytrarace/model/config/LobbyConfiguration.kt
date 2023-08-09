package net.elytrarace.model.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

@Serializable
data class LobbyConfiguration(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    @Transient
    val world: World? = Bukkit.getWorld(worldName)
) {
    val bukkitLocation: Location
        get() {
            return Location(world, x, y, z, yaw, pitch)
        }
}
