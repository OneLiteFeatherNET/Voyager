package net.elytrarace.game.phase;

import net.elytrarace.api.phase.TickingPhase;
import net.elytrarace.game.service.GameService;
import org.bukkit.Particle;

public class GamePhase extends TickingPhase {

    private static final double SPLINE_PARTICLE_OFFSET = .0D;
    private static final int SPLINE_PARTICLE_COUNT = 1;
    private static final double SPLINE_PARTICLE_EXTRA = .0D;
    private static final Particle SPLINE_PARTICLE = Particle.FLAME;

    private final GameService gameService;

    public GamePhase(GameService gameService) {
        super("Game", gameService.getPhaseScheduler(), gameService.getEventRegistrar(), 1, true);
        this.gameService = gameService;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onUpdate() {
        this.gameService.onUpdate();
        this.gameService.getCurrentMap().ifPresent(gameMapDTO -> {
            var splinePoints = gameMapDTO.splinePoints();
            var world = gameService.getPlugin().getServer().getWorld(gameMapDTO.world());
            if (world == null) return;
            splinePoints.forEach(splinePoint -> {
                world.spawnParticle(SPLINE_PARTICLE,
                        splinePoint.getX(),
                        splinePoint.getY(),
                        splinePoint.getZ(),
                        SPLINE_PARTICLE_COUNT,
                        SPLINE_PARTICLE_OFFSET,
                        SPLINE_PARTICLE_OFFSET,
                        SPLINE_PARTICLE_OFFSET,
                        SPLINE_PARTICLE_EXTRA
                );
            });
        });
    }

    @Override
    public void finish() {
        super.finish();
    }
}
