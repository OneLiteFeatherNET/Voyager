package net.elytrarace.phases

import net.elytrarace.Voyager
import net.elytrarace.model.dto.GameMapSession
import net.elytrarace.phase.TickingPhase
import net.elytrarace.utils.SHOW_LINE_COUNT
import net.elytrarace.utils.SHOW_LINE_EXTRA
import net.elytrarace.utils.SHOW_LINE_OFFSET
import net.elytrarace.utils.SHOW_LINE_PARTICLE

class GamePhase(javaPlugin: Voyager, val mapSession: GameMapSession) : TickingPhase("Game", javaPlugin, 20, true) {

    override fun onStart() {
        super.onStart()
    }
    override fun onUpdate() {
        this.mapSession.splineLocations.forEach {
            this.mapSession.world.spawnParticle(
                SHOW_LINE_PARTICLE,
                it.x,
                it.y,
                it.z,
                SHOW_LINE_COUNT,
                SHOW_LINE_OFFSET,
                SHOW_LINE_OFFSET,
                SHOW_LINE_OFFSET,
                SHOW_LINE_EXTRA
            )
        }
    }
}