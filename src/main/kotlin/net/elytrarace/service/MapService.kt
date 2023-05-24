package net.elytrarace.service

import net.elytrarace.Voyager
import net.elytrarace.models.ElytraMap
import net.elytrarace.placeholder.GameMapSession
import net.elytrarace.placeholder.LobbyMapSession
import net.elytrarace.utils.*
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
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