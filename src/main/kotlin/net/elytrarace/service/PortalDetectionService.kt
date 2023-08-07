package net.elytrarace.service

import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.model.dto.PortalDTO
import net.elytrarace.utils.precision
import org.apache.commons.geometry.euclidean.threed.line.Lines3D

class PortalDetectionService {

    private val index = 0
    fun checkPlayer(player: ElytraPlayer, portalDTO: PortalDTO): Boolean {
        if (player.positionQueue.size < 3) return false

        val position = player.positionQueue[index]
        val positionSecond = player.positionQueue[index + 1]
        val positionThird = player.positionQueue[index + 2]

        if (position.isZero(precision)) return false
        if (positionSecond.isZero(precision)) return false
        if (positionThird.isZero(precision)) return false

        if (position.vectorTo(positionSecond).isZero(precision)) return false
        if (positionSecond.vectorTo(positionThird).isZero(precision)) return false
        if (position.vectorTo(positionThird).isZero(precision)) return false

        val firstLine = Lines3D.fromPoints(position, positionSecond, precision)
        val secondLine = Lines3D.fromPoints(positionSecond, positionThird, precision)
        val thirdLine = Lines3D.fromPoints(position, positionThird, precision)

        val firstSeg = Lines3D.segmentFromPoints(position, positionSecond, precision)
        val secondSeg = Lines3D.segmentFromPoints(positionSecond, positionThird, precision)
        val thirdSeg = Lines3D.segmentFromPoints(position, positionThird, precision)

        val firstLineIntersection = portalDTO.plane.intersection(firstLine)
        val secondLineIntersection = portalDTO.plane.intersection(secondLine)
        val thirdLineIntersection = portalDTO.plane.intersection(thirdLine)

        if (firstLineIntersection != null && portalDTO.bounds.contains(firstLineIntersection, precision) && firstSeg.contains(firstLineIntersection) && portalDTO.regionBSPTree3D.contains(firstLineIntersection)) return true
        if (secondLineIntersection != null && portalDTO.bounds.contains(secondLineIntersection, precision) && secondSeg.contains(secondLineIntersection) && portalDTO.regionBSPTree3D.contains(secondLineIntersection)) return true
        if (thirdLineIntersection != null && portalDTO.bounds.contains(thirdLineIntersection, precision) && thirdSeg.contains(thirdLineIntersection) && portalDTO.regionBSPTree3D.contains(thirdLineIntersection)) return true
        return false
    }

}