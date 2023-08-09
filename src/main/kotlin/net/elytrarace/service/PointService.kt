package net.elytrarace.service

import net.elytrarace.phases.GamePhase
import kotlin.math.round

class PointService {

    private fun calculateDifficultyFactor(objectCount: Int, timeInMilli: Long): Double {
        return 1.0 + (objectCount / objectCount) + (timeInMilli / 100_000.0)
    }

    fun givePointsToPlayer(map: GamePhase) {
        val objectCount = map.mapSession.sortedPortals.size
        var fullPoints = map.completePoints
        val playerCount = map.mapSession.playerSessions.values.size
        val playerWithPoints = map.mapSession.playerSessions.values.mapIndexed { index, player ->
            val difficultyFactor = calculateDifficultyFactor(objectCount, player.timeDiff?.toMillis() ?: 0)
            val points = round((fullPoints * (playerCount - index)) / ((playerCount * (playerCount + 1.0)) / 2) * difficultyFactor).toInt()
            fullPoints -= points
            player.copy(points = points)
        }
        playerWithPoints.forEach {
            map.mapSession.playerSessions.put(it.player.entityId, it)
        }
    }
}