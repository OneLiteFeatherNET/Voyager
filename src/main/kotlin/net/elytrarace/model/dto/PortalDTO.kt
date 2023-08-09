package net.elytrarace.model.dto

import net.elytrarace.model.dbo.ElytraMap
import net.elytrarace.utils.precision
import org.apache.commons.geometry.euclidean.threed.*
import org.apache.commons.geometry.euclidean.threed.shape.Parallelepiped

data class PortalDTO(
    val corners: List<Vector3D>,
    val index: Int,
    val elytraMap: ElytraMap
) {
    val plane: Plane = Planes.fromPoints(corners, precision)
    val bounds: Bounds3D = Bounds3D.from(corners)
    val regionBSPTree3D: RegionBSPTree3D = Parallelepiped.fromBounds(plane).toTree()
}
