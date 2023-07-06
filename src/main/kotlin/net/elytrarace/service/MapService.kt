package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.config.PluginMode
import net.elytrarace.models.ElytraMap
import net.elytrarace.placeholder.GameMapSession
import net.elytrarace.placeholder.LobbyMapSession
import net.elytrarace.placeholder.PlayerSession
import net.elytrarace.utils.*
import net.elytrarace.utils.extensions.interpolate
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
import org.bukkit.WorldCreator
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.util.Vector
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant

class MapService(val voyager: Voyager) {
    val mapSessions = mutableListOf<GameMapSession>()
    val lobbyMapSession: LobbyMapSession

    private val precision: Precision.DoubleEquivalence = Precision.doubleEquivalenceOfEpsilon(1e-6)
    private val intersectionPoint = mutableMapOf<Player, MutableList<FallingBlock>>()
    private val debugBlockData = Bukkit.createBlockData(Material.SAND)

    init {
        val lobbyWorld = voyager.configService.lobbyWorld
        if (lobbyWorld?.world != null) {
            lobbyMapSession = LobbyMapSession(lobbyWorld.world, lobbyWorld)
            loadMaps()
        } else {
            lobbyMapSession = TODO()
        }
        voyager.server.scheduler.runTaskTimerAsynchronously(voyager, this::showMapLines, SHOW_LINE_DELAY, SHOW_LINE_TIMER)
        if (voyager.configService.config.pluginMode == PluginMode.TESTING) {
            transaction {
                mapSessions.forEach { session ->
                    session.elytraMap.rings.forEach {
                        it.locations.forEach {
                            val shulker = it.bukkitLocation.world.spawnFallingBlock(
                                it.bukkitLocation.clone().add(0.5, 0.0, 0.5),
                                Bukkit.createBlockData(Material.SAND)
                            )
                            shulker.isGlowing = true
                            shulker.setGravity(false)
                            shulker.dropItem = false
                            shulker.velocity = shulker.velocity.add(Vector(0.0, 0.0, 0.0))
                            shulker.ticksLived = Int.MAX_VALUE
                            shulker.isVisibleByDefault = false
                            shulker.shouldAutoExpire(false)
                        }
                    }
                }
            }
        }
    }

    fun reloadActiveMaps() {
        mapSessions.clear()
        loadMaps()
    }

    private fun loadMaps() = transaction {
        val maps = ElytraMap.all()
        maps.forEach {
            val world = Bukkit.createWorld(WorldCreator.name(it.world).generator(VOID_GEN_STRING))
            if (world != null) {
                mapSessions.add(
                    GameMapSession(world, it)
                )
            }
        }
    }

    private fun showMapLines() {
        voyager.playerService.playerSessions.values.forEach {
            val mapSession = it.mapSession
            if (mapSession is LobbyMapSession) {
                return@forEach
            }
            // Get the game map session
            val gameMapSession = mapSession as GameMapSession
            if (it.stackPlayerPositions.isNotEmpty()) {
                val ringLocations = it.stackPlayerPositions.windowed(6)
                val firstSix = (ringLocations.last() + ringLocations.take(4).reduce { acc, locations -> acc + locations }).toMutableList()
                val first = (interpolate(firstSix, 0, 50) + interpolate(firstSix, 2, 50))
                val locs = ringLocations.map { locations ->
                    interpolate(locations, 0, 50) + interpolate(locations, 2, 50)
                }
                val finalPos = first + (locs.reduce { acc, lists -> acc + lists })
                val nextRing = transaction {
                    return@transaction gameMapSession.sortedRings.higher(it.lastRing)
                }
                val latestRing = transaction {
                    return@transaction gameMapSession.sortedRings.last()
                }
                val bukkitLocations = transaction {
                    return@transaction (nextRing ?: latestRing).locations.map { it.bukkitLocation }
                }
                finalPos.windowed(2).forEach fs@{ locations ->
                    if (locations.size == 2) {
                        return@fs
                    }
                    val found = checkPair(Pair(locations[0], locations[1]), bukkitLocations, it.player);
                    if (found && nextRing != null) {
                        it.timeStampForRings[nextRing] = Instant.now()
                        updateScoreboard(it)
                        voyager.playerService.playerSessions[it.player] =
                                it.copy(lastRing = nextRing)
                        if (nextRing == latestRing) {
                            this.intersectionPoint.getOrDefault(it.player, mutableListOf()).forEach {block ->
                                block.remove()
                            }
                            voyager.playerService.finishMap(it)
                        }
                    }
                }
                finalPos.windowed(2, step = 2).forEach fs@{ locations ->
                    if (locations.size == 2) {
                        return@fs
                    }
                    val found = checkPair(Pair(locations[0], locations[1]), bukkitLocations, it.player);
                    if (found && nextRing != null) {
                        it.timeStampForRings[nextRing] = Instant.now()
                        updateScoreboard(it)
                        voyager.playerService.playerSessions[it.player] =
                                it.copy(lastRing = nextRing)
                        if (nextRing == latestRing) {
                            this.intersectionPoint.getOrDefault(it.player, mutableListOf()).forEach {block ->
                                block.remove()
                            }
                            voyager.playerService.finishMap(it)
                        }
                    }
                }
                if (voyager.configService.config.pluginMode == PluginMode.TESTING) {
                    finalPos.forEach { loc ->
                        loc.world.spawnParticle(
                                SHOW_DEBUG_LINE_PARTICLE,
                                loc,
                                SHOW_LINE_COUNT,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_EXTRA
                        )
                    }
                    it.stackPlayerPositions.forEach { loc ->
                        loc.world.spawnParticle(
                                SHOW_DEBUG_LOC_LINE_PARTICLE,
                                loc,
                                SHOW_LINE_COUNT,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_EXTRA
                        )
                    }
                    it.intersectionPositions.forEach { loc ->
                        loc.world.spawnParticle(
                                SHOW_DEBUG_INTERSECTION_LINE_PARTICLE,
                                loc,
                                SHOW_LINE_COUNT,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_OFFSET,
                                SHOW_LINE_EXTRA
                        )
                    }
                }
            }
        }
        this.mapSessions.forEach { session ->
            session.splineLocations.forEach { location ->
                location.world.spawnParticle(
                    SHOW_LINE_PARTICLE,
                    location,
                    SHOW_LINE_COUNT,
                    SHOW_LINE_OFFSET,
                    SHOW_LINE_OFFSET,
                    SHOW_LINE_OFFSET,
                    SHOW_LINE_EXTRA
                )
            }
        }
    }

    fun checkPair(
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
            voyager.playerService.playerSessions[player]?.intersectionPositions?.push(Location(player.world, intersection.x,intersection.y, intersection.z))
            return true
        }
        val lineCasts = regionBSPTree3D.linecast(line)
        return lineCasts.any {
            val found = (regionBSPTree3D.contains(it.point) && seg.contains(it.point) && bounds.contains(it.point))
            if (found) {
                it.point
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
                voyager.playerService.playerSessions[player]?.intersectionPositions?.push(Location(player.world, it.point.x,it.point.y, it.point.z))
            }
            found
        }
    }

    fun updateScoreboard(playerSession: PlayerSession) {
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


}