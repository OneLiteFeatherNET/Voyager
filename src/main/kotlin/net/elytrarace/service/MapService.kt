package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.config.PluginMode
import net.elytrarace.models.ElytraMap
import net.elytrarace.placeholder.GameMapSession
import net.elytrarace.placeholder.LobbyMapSession
import net.elytrarace.utils.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.WorldCreator
import org.bukkit.util.Vector
import org.jetbrains.exposed.sql.transactions.transaction

class MapService(voyager: Voyager) {
    val mapSessions = mutableListOf<GameMapSession>()
    val lobbyMapSession: LobbyMapSession

    init {
        val lobbyWorld = voyager.configService.lobbyWorld
        if (lobbyWorld?.world != null) {
            lobbyMapSession = LobbyMapSession(lobbyWorld.world, lobbyWorld)
            loadMaps()
        } else {
            lobbyMapSession = TODO()
        }
        voyager.server.scheduler.runTaskTimerAsynchronously(voyager, showMapLines(), SHOW_LINE_DELAY, SHOW_LINE_TIMER)
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

    private fun showMapLines() = Runnable {
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


}