package net.elytrarace.model.dto

import org.apache.commons.geometry.euclidean.threed.Vector3D

data class PortalDTO(
    val corners: List<Vector3D>
)
