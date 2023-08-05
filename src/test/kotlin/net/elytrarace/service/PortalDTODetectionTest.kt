package net.elytrarace.service

import io.mockk.mockk
import net.elytrarace.model.dto.ElytraPlayer
import net.elytrarace.model.dto.PortalDTO
import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class PortalDTODetectionTest {

    @Test
    fun testCheckPlayer_vertical_portal() {
        val portalDetectionService = PortalDetectionService()
        val portalDTO = PortalDTO(
            arrayListOf(
                Vector3D.of(0.0,0.0,0.0),
                Vector3D.of(20.0,0.0,0.0),
                Vector3D.of(20.0,10.0,0.0),
                Vector3D.of(0.0,10.0,0.0)
            ),
            0,
            mockk()
        )
        val fakePlayer = ElytraPlayer(lastPortal = mockk(), player = mockk<Player>(), mapSession = mockk(), startTime = Instant.now(), timeStampForRings = mutableMapOf())
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,-5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,0.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,5.0))
        Assertions.assertTrue(portalDetectionService.checkPlayer(fakePlayer, portalDTO))
    }

    @Test
    fun testCheckPlayer_horizontal_portal() {
        val portalDetectionService = PortalDetectionService()
        val portalDTO = PortalDTO(
            arrayListOf(
                Vector3D.of(0.0,0.0,0.0),
                Vector3D.of(20.0,0.0,0.0),
                Vector3D.of(20.0,0.0,10.0),
                Vector3D.of(0.0,0.0,10.0)
            ),
            0,
            mockk()
        )
        val fakePlayer = ElytraPlayer(lastPortal = mockk(), player = mockk<Player>(), mapSession = mockk(), startTime = Instant.now(), timeStampForRings = mutableMapOf())
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,-5.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,0.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,5.0))
        Assertions.assertTrue(portalDetectionService.checkPlayer(fakePlayer, portalDTO))
    }

    @Test
    fun testCheckPlayer_horizontal_and_vertical_portal() {
        val portalDetectionService = PortalDetectionService()
        val portalDTO = PortalDTO(
            arrayListOf(
                Vector3D.of(0.0,0.0,0.0),
                Vector3D.of(20.0,0.0,0.0),
                Vector3D.of(20.0,10.0,10.0),
                Vector3D.of(0.0,10.0,10.0)
            ),
            0,
            mockk()
        )
        val fakePlayer = ElytraPlayer(lastPortal = mockk(), player = mockk<Player>(), mapSession = mockk(), startTime = Instant.now(), timeStampForRings = mutableMapOf())
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,-5.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,0.0,5.0))
        fakePlayer.positionQueue.add(0,Vector3D.of(15.0,5.0,5.0))
        Assertions.assertTrue(portalDetectionService.checkPlayer(fakePlayer, portalDTO))
    }

}