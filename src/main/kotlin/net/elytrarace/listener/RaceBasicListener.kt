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
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.util.Vector
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

class RaceBasicListener(
    val voyager: Voyager
) : Listener {
    private val precision: Precision.DoubleEquivalence = Precision.doubleEquivalenceOfEpsilon(1e-6)
    private val intersectionPoint = mutableMapOf<Player, MutableList<FallingBlock>>()
    private val debugBlockData = Bukkit.createBlockData(Material.SAND)

    @EventHandler
    fun checkPortal(event: PlayerMoveEvent) {
        val player = event.player
        // Gets the player session
        val session = voyager.playerService.playerSessions[player] ?: return
        // Checks if the player on the lobby
        if (session.mapSession is LobbyMapSession) {
            return
        }
        // Get the game map session
        val gameMapSession = session.mapSession as GameMapSession
        // Check if last ring null - Checks if the first ring/portal
        if (session.lastRing == null) {
            // Gets the first ring
            val firstRing = transaction {
                return@transaction gameMapSession.sortedRings.first()
            }
            // Map the locations to bukkit locations
            val bukkitLocations = transaction {
                return@transaction firstRing.locations.map { it.bukkitLocation }
            }
            val to = event.to
            val from = event.from
            val found = checkPair(Pair(from, to), bukkitLocations, player) || checkPair(
                Pair(from, player.location),
                bukkitLocations,
                player
            ) || checkPair(Pair(to, player.location), bukkitLocations, player)

            if (found) {
                session.timeStampForRings[firstRing] = Instant.now()
                val newSession = session.copy(startTime = Instant.now(), lastRing = firstRing)
                updateScoreboard(newSession)
                voyager.playerService.playerSessions[player] = newSession
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
            val found = checkPair(Pair(from, to), bukkitLocations, player) || checkPair(
                Pair(from, player.location),
                bukkitLocations,
                player
            ) || checkPair(Pair(to, player.location), bukkitLocations, player)

            if (found && nextRing != null) {
                session.timeStampForRings[nextRing] = Instant.now()
                updateScoreboard(session)
                voyager.playerService.playerSessions[player] =
                    session.copy(lastRing = nextRing)
                if (nextRing == latestRing) {
                    this.intersectionPoint.getOrDefault(player, mutableListOf()).forEach {
                        it.remove()
                    }
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

    private fun checkPair(
        locationPair: Pair<Location, Location>,
        bukkitLocations: List<Location>,
        player: Player
    ): Boolean {
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
            val fallingBlockSpawn = Location(locationPair.first.world, intersection.x, intersection.y, intersection.z)
            val block = player.world.spawnFallingBlock(fallingBlockSpawn, debugBlockData)
            block.isGlowing = true
            block.setGravity(false)
            block.dropItem = false
            block.velocity = block.velocity.add(Vector(0.0, 0.0, 0.0))
            block.ticksLived = Int.MAX_VALUE
            block.isVisibleByDefault = false
            block.shouldAutoExpire(false)
            this.intersectionPoint.getOrPut(player) { mutableListOf() }
                .add(block)
            return true
        }
        val lineCasts = regionBSPTree3D.linecast(line)
        return lineCasts.any {
            val found = (regionBSPTree3D.contains(it.point) && seg.contains(it.point) && bounds.contains(it.point))
            if (found) {
                val fallingBlockSpawn = Location(locationPair.first.world, it.point.x, it.point.y, it.point.z)
                val block = player.world.spawnFallingBlock(fallingBlockSpawn, debugBlockData)
                block.isGlowing = true
                block.setGravity(false)
                block.dropItem = false
                block.velocity = block.velocity.add(Vector(0.0, 0.0, 0.0))
                block.ticksLived = Int.MAX_VALUE
                block.isVisibleByDefault = false
                block.shouldAutoExpire(false)
                this.intersectionPoint.getOrPut(player) { mutableListOf() }
                    .add(block)
            }
            found
        }
    }
}