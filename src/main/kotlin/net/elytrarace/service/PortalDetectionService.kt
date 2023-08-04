package net.elytrarace.service

import net.elytrarace.model.ElytraPlayer
import net.elytrarace.model.Portal
import org.apache.commons.geometry.euclidean.threed.Bounds3D
import org.apache.commons.geometry.euclidean.threed.Planes
import org.apache.commons.geometry.euclidean.threed.line.Lines3D
import org.apache.commons.numbers.core.Precision

class PortalDetectionService {
    private val precision: Precision.DoubleEquivalence = Precision.doubleEquivalenceOfEpsilon(1e-6)
    fun checkPlayer(player: ElytraPlayer, portal: Portal): Boolean {
        if (player.positionQueue.size < 3) return false
        val index = 0
        val position = player.positionQueue[index]
        val positionSecond = player.positionQueue[index+1]
        val positionThird = player.positionQueue[index+2]

        val plane = Planes.fromPoints(portal.corners, precision)

        val bounds = Bounds3D.from(portal.corners)

        val firstLine = Lines3D.fromPoints(position, positionSecond, precision)
        val secondLine = Lines3D.fromPoints(positionSecond, positionThird, precision)
        val thirdLine = Lines3D.fromPoints(position, positionThird, precision)

        val firstLineIntersection = plane.intersection(firstLine)
        val secondLineIntersection = plane.intersection(secondLine)
        val thirdLineIntersection = plane.intersection(thirdLine)
        return (firstLineIntersection != null && bounds.contains(firstLineIntersection)) || (secondLineIntersection != null && bounds.contains(secondLineIntersection)) || (thirdLineIntersection!= null && bounds.contains(thirdLineIntersection))
    }

}