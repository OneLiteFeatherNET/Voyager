package net.elytrarace.listener

import net.elytrarace.Voyager
import net.elytrarace.placeholder.GameMapSession
import net.elytrarace.placeholder.LobbyMapSession
import net.elytrarace.placeholder.PlayerSession
import net.elytrarace.utils.OBJECTIVES_NAME
import net.kyori.adventure.text.Component
import org.apache.commons.geometry.euclidean.threed.Bounds3D
import org.apache.commons.geometry.euclidean.threed.Planes
import org.apache.commons.geometry.euclidean.threed.Vector3D
import org.apache.commons.geometry.euclidean.threed.line.Lines3D
import org.apache.commons.geometry.euclidean.threed.shape.Parallelepiped
import org.apache.commons.numbers.core.Precision
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

class RaceBasicListener(
    val voyager: Voyager
) : Listener {
    private val precision: Precision.DoubleEquivalence = Precision.doubleEquivalenceOfEpsilon(1e-6)

    @EventHandler
    fun checkPortal(event: PlayerMoveEvent) {
        val player = event.player
        val session = voyager.playerService.playerSessions[player] ?: return
        if (session.mapSession is LobbyMapSession) {
            return
        }
        val gameMapSession = session.mapSession as GameMapSession
        if (session.lastRing == null) {
            val firstRing = transaction {
                return@transaction gameMapSession.sortedRings.first()
            }
            val bukkitLocations = transaction {

                return@transaction firstRing.locations.map { it.bukkitLocation }
            }
            val to = event.to
            val from = event.from
            val found = checkPair(Pair(from, to), bukkitLocations) || checkPair(
                Pair(from, player.location),
                bukkitLocations
            ) || checkPair(Pair(to, player.location), bukkitLocations)

            if (found) {
                session.timeStampForRings[firstRing] = Instant.now()
                updateScoreboard(session)
                voyager.playerService.playerSessions[player] =
                    session.copy(startTime = Instant.now(), lastRing = firstRing)
            }
        } else {
            val nextRing = transaction {
                return@transaction gameMapSession.sortedRings.higher(session.lastRing)
            }
            val latestRing = transaction {
                return@transaction gameMapSession.sortedRings.last()
            }
            val bukkitLocations = transaction {
                return@transaction (nextRing ?: latestRing).locations.map { it.bukkitLocation }
            }
            val to = event.to
            val from = event.from
            val found = checkPair(Pair(from, to), bukkitLocations) || checkPair(
                Pair(from, player.location),
                bukkitLocations
            ) || checkPair(Pair(to, player.location), bukkitLocations)

            if (found && nextRing != null) {
                session.timeStampForRings[nextRing] = Instant.now()
                updateScoreboard(session)
                voyager.playerService.playerSessions[player] =
                    session.copy(lastRing = nextRing)
                if (nextRing == latestRing) {
                    voyager.playerService.finishMap(session)
                }
            }
        }
    }

    private fun updateScoreboard(playerSession: PlayerSession) {
        val sb = playerSession.player.scoreboard
        val objective = sb.getObjective(OBJECTIVES_NAME) ?: sb.registerNewObjective(
            OBJECTIVES_NAME,
            Criteria.DUMMY,
            Component.empty()
        )
        objective.displaySlot = DisplaySlot.SIDEBAR
        playerSession.timeStampForRings.forEach { (ring, time) ->
            val startTime = playerSession.startTime ?: return@forEach
            val diff = Duration.ofMillis(time.minusMillis(startTime.toEpochMilli()).toEpochMilli())
            val minutes = diff.toMinutesPart().toString().padStart(2, '0')
            val seconds = diff.toSecondsPart().toString().padStart(2, '0')
            val millis = diff.toMillisPart().toString().drop(1).padStart(3, '0')
            val score = objective.getScore("$minutes:$seconds:$millis")
            transaction {
                score.score = ring.index
            }
        }
        playerSession.player.scoreboard = sb

    }

    private fun checkPair(locationPair: Pair<Location, Location>, bukkitLocations: List<Location>): Boolean {
        val start = Vector3D.of(locationPair.first.x, locationPair.first.y, locationPair.first.z)
        val end = Vector3D.of(locationPair.second.x, locationPair.second.y, locationPair.second.z)
        if (start.eq(end, precision)) {
            return false
        }
        val seg = Lines3D.segmentFromPoints(start, end, precision)
        val line = Lines3D.fromPoints(start, end, precision)
        val vectors = bukkitLocations.map { Vector3D.of(it.x, it.y, it.z) }.toList()
        val plane = Planes.fromPoints(vectors, precision)
        val bounds = Bounds3D.from(vectors)
        val regionBSPTree3D = Parallelepiped.fromBounds(plane).toTree()

        val intersection = plane.intersection(line)
        if (intersection != null && bounds.contains(intersection) && regionBSPTree3D.contains(intersection) && seg.contains(
                intersection
            )
        ) {
            return true
        }
        val lineCasts = regionBSPTree3D.linecast(line)
        return lineCasts.any { (regionBSPTree3D.contains(it.point) && seg.contains(it.point) && bounds.contains(it.point)) }
    }
}