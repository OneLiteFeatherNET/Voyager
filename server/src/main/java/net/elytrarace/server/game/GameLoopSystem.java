package net.elytrarace.server.game;

import net.elytrarace.server.physics.ElytraPhysics;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.physics.RingCollisionDetector;
import net.elytrarace.server.player.PlayerService;
import net.minestom.server.coordinate.Vec;

import java.util.List;
import java.util.UUID;

/**
 * Main game loop system that runs once per tick.
 * <p>
 * For each player in the session it:
 * <ol>
 *   <li>Computes updated elytra physics velocity</li>
 *   <li>Records the previous position</li>
 *   <li>Applies the new velocity to the player</li>
 *   <li>Checks ring collisions and updates scoring</li>
 * </ol>
 * <p>
 * This class is stateless apart from the references it holds — all mutable
 * game state lives in {@link GameSession}.
 */
public final class GameLoopSystem {

    private final GameSession session;
    private final PlayerService playerService;

    public GameLoopSystem(GameSession session, PlayerService playerService) {
        this.session = session;
        this.playerService = playerService;
    }

    /**
     * Executes one tick of the game loop.
     */
    public void tick() {
        var currentMap = session.getCupFlow().getCurrentMap();
        if (currentMap.isEmpty()) {
            return;
        }
        List<Ring> rings = currentMap.get().rings();

        for (UUID playerId : session.getPlayers()) {
            var playerOpt = playerService.getPlayer(playerId);
            if (playerOpt.isEmpty()) {
                continue;
            }
            var player = playerOpt.get();

            // 1. Physics update
            Vec oldVelocity = session.getVelocity(playerId);
            Vec newVelocity = ElytraPhysics.computeNextVelocity(
                    oldVelocity, player.getPosition().pitch(), player.getPosition().yaw());
            session.setVelocity(playerId, newVelocity);

            // 2. Remember previous position (for collision line segment)
            Vec prevPos = new Vec(player.getPosition().x(), player.getPosition().y(), player.getPosition().z());

            // 3. Predict next position (setVelocity doesn't move the player until next tick)
            Vec currPos = prevPos.add(newVelocity);

            // 4. Apply velocity to player
            player.setVelocity(newVelocity);

            // 5. Ring collision detection
            for (int i = 0; i < rings.size(); i++) {
                Ring ring = rings.get(i);
                if (!session.hasPassedRing(playerId, i)
                        && RingCollisionDetector.checkPassthrough(ring, prevPos, currPos)) {
                    session.markRingPassed(playerId, i);
                    // Scoring is handled by ECS systems (RingCollisionSystem,
                    // CompletionDetectionSystem) when the game runs through the
                    // EntityManager loop. This legacy class remains only for the
                    // unit-tested helper math.
                }
            }
        }
    }
}
