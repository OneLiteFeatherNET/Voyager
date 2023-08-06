package net.elytrarace.service

import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.model.dto.PortalDTO
import org.apache.commons.geometry.euclidean.threed.Bounds3D
import org.apache.commons.geometry.euclidean.threed.Planes
import org.apache.commons.geometry.euclidean.threed.line.Lines3D
import org.apache.commons.geometry.euclidean.threed.shape.Parallelepiped
import org.apache.commons.numbers.core.Precision

class PortalDetectionService {
    private val precision: Precision.DoubleEquivalence = Precision.doubleEquivalenceOfEpsilon(1e-6)
    private val index = 0
    fun checkPlayer(player: ElytraPlayer, portalDTO: PortalDTO): Boolean {
        if (player.positionQueue.size < 3) return false

        val position = player.positionQueue[index]
        val positionSecond = player.positionQueue[index+1]
        val positionThird = player.positionQueue[index+2]

        if (position.isZero(precision)) return false
        if (positionSecond.isZero(precision)) return false
        if (positionThird.isZero(precision)) return false

        if (position.vectorTo(positionSecond).isZero(precision)) return false
        if (positionSecond.vectorTo(positionThird).isZero(precision)) return false
        if (position.vectorTo(positionThird).isZero(precision)) return false

        val plane = Planes.fromPoints(portalDTO.corners, precision)

        val bounds = Bounds3D.from(portalDTO.corners)

        val firstLine = Lines3D.fromPoints(position, positionSecond, precision)
        val secondLine = Lines3D.fromPoints(positionSecond, positionThird, precision)
        val thirdLine = Lines3D.fromPoints(position, positionThird, precision)

        val firstSeg = Lines3D.segmentFromPoints(position, positionSecond, precision)
        val secondSeg = Lines3D.segmentFromPoints(positionSecond, positionThird, precision)
        val thirdSeg = Lines3D.segmentFromPoints(position, positionThird, precision)

        val regionBSPTree3D = Parallelepiped.fromBounds(plane).toTree()

        val firstLineIntersection = plane.intersection(firstLine)
        val secondLineIntersection = plane.intersection(secondLine)
        val thirdLineIntersection = plane.intersection(thirdLine)

        if (firstLineIntersection != null && bounds.contains(firstLineIntersection, precision) && firstSeg.contains(firstLineIntersection) && regionBSPTree3D.contains(firstLineIntersection)) return true
        if (secondLineIntersection != null && bounds.contains(secondLineIntersection, precision) && secondSeg.contains(secondLineIntersection) && regionBSPTree3D.contains(secondLineIntersection)) return true
        if (thirdLineIntersection != null && bounds.contains(thirdLineIntersection, precision) && thirdSeg.contains(thirdLineIntersection) && regionBSPTree3D.contains(thirdLineIntersection)) return true
        return false
    }

}