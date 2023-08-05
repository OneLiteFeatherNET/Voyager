package net.elytrarace.model.dto

import net.elytrarace.model.dbo.Portal
import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.bukkit.entity.Player
import java.time.Instant

data class ElytraPlayer(
    val positionQueue: ArrayList<Vector3D> = ArrayList(),
    val lastPortal: Portal? = null,
    val startTime: Instant?,
    val timeStampForRings: MutableMap<Portal, Instant> = mutableMapOf(),
    val player: Player,
    val mapSession: MapSession,
)
