package net.elytrarace.service

import net.elytrarace.model.ElytraPlayer
import net.elytrarace.model.Portal
import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PortalDetectionTest {

    @Test
    fun testCheckPlayer_vertical_portal() {
        val portalDetectionService = PortalDetectionService()
        val portal = Portal(
            arrayListOf(
                Vector3D.of(0.0,0.0,0.0),
                Vector3D.of(20.0,0.0,0.0),
                Vector3D.of(20.0,10.0,0.0),
                Vector3D.of(0.0,10.0,0.0)
            )
        )
        val fakePlayer = ElytraPlayer()
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,-5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,0.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,5.0))
        Assertions.assertTrue(portalDetectionService.checkPlayer(fakePlayer, portal))
    }

    @Test
    fun testCheckPlayer_horizontal_portal() {
        val portalDetectionService = PortalDetectionService()
        val portal = Portal(
            arrayListOf(
                Vector3D.of(0.0,0.0,0.0),
                Vector3D.of(20.0,0.0,0.0),
                Vector3D.of(20.0,0.0,10.0),
                Vector3D.of(0.0,0.0,10.0)
            )
        )
        val fakePlayer = ElytraPlayer()
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,-5.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,0.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,5.0))
        Assertions.assertTrue(portalDetectionService.checkPlayer(fakePlayer, portal))
    }

    @Test
    fun testCheckPlayer_horizontal_and_vertical_portal() {
        val portalDetectionService = PortalDetectionService()
        val portal = Portal(
            arrayListOf(
                Vector3D.of(0.0,0.0,0.0),
                Vector3D.of(20.0,0.0,0.0),
                Vector3D.of(20.0,10.0,10.0),
                Vector3D.of(0.0,10.0,10.0)
            )
        )
        val fakePlayer = ElytraPlayer()
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,-5.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,0.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,5.0))
        Assertions.assertTrue(portalDetectionService.checkPlayer(fakePlayer, portal))
    }

}