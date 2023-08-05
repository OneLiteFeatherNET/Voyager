package net.elytrarace.model.dto

import net.elytrarace.model.dbo.ElytraMap
import org.apache.commons.geometry.euclidean.threed.Vector3D

data class PortalDTO(
    val corners: List<Vector3D>,
    val index: Int,
    val elytraMap: ElytraMap
)
