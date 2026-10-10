package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.physics.Ring;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
@org.junit.jupiter.api.Disabled("Requires full Minestom player support - run manually")
class RingCollisionSystemTest {

    private static final Ring RING_AT_ORIGIN = new Ring(
            new Vec(0, 60, 0), new Vec(0, 0, 1), 5.0, 10);
    private static final Ring RING_AT_Z10 = new Ring(
            new Vec(0, 60, 10), new Vec(0, 0, 1), 5.0, 25);

    private EntityManager setupEntityManager(MapDefinition map) {
        var entityManager = new EntityManager();

        // Game entity with active map
        var gameEntity = new Entity();
        var activeMap = new ActiveMapComponent();
        activeMap.setCurrentMap(map);
        gameEntity.addComponent(activeMap);
        entityManager.addEntity(gameEntity);

        entityManager.addSystem(new RingCollisionSystem(entityManager));
        return entityManager;
    }

    @Test
    void ringIsDetectedAndScoreUpdated(Env env) {
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN), new Pos(0, 60, -10));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        // Player at z=2, previousPosition at z=-2 → segment crosses ring at z=0
        var player = env.createPlayer(instance, new Pos(0, 60, 2));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setPreviousPosition(new Pos(0, 60, -2));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var tracker = new RingTrackerComponent();
        playerEntity.addComponent(tracker);

        var score = new ScoreComponent();
        playerEntity.addComponent(score);

        entityManager.addEntity(playerEntity);
        entityManager.update(1.0f / 20.0f);

        assertThat(tracker.hasPassed(0)).isTrue();
        assertThat(score.getRingPoints()).isEqualTo(10);
    }

    @Test
    void alreadyPassedRingIsNotCountedTwice(Env env) {
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN), new Pos(0, 60, -10));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 2));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setPreviousPosition(new Pos(0, 60, -2));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var tracker = new RingTrackerComponent();
        tracker.markPassed(0); // Already passed
        playerEntity.addComponent(tracker);

        var score = new ScoreComponent();
        playerEntity.addComponent(score);

        entityManager.addEntity(playerEntity);
        entityManager.update(1.0f / 20.0f);

        // Score should remain zero since the ring was already marked
        assertThat(score.getRingPoints()).isZero();
    }

    @Test
    void secondRingIsIgnoredUntilFirstIsPassed(Env env) {
        // Ring 0 at origin, ring 1 at z=10. Player segment crosses ring 1 but NOT ring 0.
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN, RING_AT_Z10), new Pos(0, 60, -20));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        // Player at z=14, previousPosition at z=6 → crosses ring 1 (z=10) but not ring 0 (z=0)
        var player = env.createPlayer(instance, new Pos(0, 60, 14));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setPreviousPosition(new Pos(0, 60, 6));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var tracker = new RingTrackerComponent();
        playerEntity.addComponent(tracker);

        var score = new ScoreComponent();
        playerEntity.addComponent(score);

        entityManager.addEntity(playerEntity);
        entityManager.update(1.0f / 20.0f);

        // Ring 1 must NOT be counted because ring 0 was not passed first
        assertThat(tracker.hasPassed(0)).isFalse();
        assertThat(tracker.hasPassed(1)).isFalse();
        assertThat(score.getRingPoints()).isZero();
    }

    @Test
    void missedRingDoesNotUpdateScore(Env env) {
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN), new Pos(0, 60, -10));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        // Player far off to the side — segment clearly misses the ring
        var player = env.createPlayer(instance, new Pos(100, 60, 2));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setPreviousPosition(new Pos(100, 60, -2));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var tracker = new RingTrackerComponent();
        playerEntity.addComponent(tracker);

        var score = new ScoreComponent();
        playerEntity.addComponent(score);

        entityManager.addEntity(playerEntity);
        entityManager.update(1.0f / 20.0f);

        assertThat(tracker.passedCount()).isZero();
        assertThat(score.getRingPoints()).isZero();
    }

    @Test
    void notFlyingSkipsCollisionCheck(Env env) {
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN), new Pos(0, 60, -10));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 2));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(false); // Not flying
        flight.setPreviousPosition(new Pos(0, 60, -2));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var tracker = new RingTrackerComponent();
        playerEntity.addComponent(tracker);

        var score = new ScoreComponent();
        playerEntity.addComponent(score);

        entityManager.addEntity(playerEntity);
        entityManager.update(1.0f / 20.0f);

        assertThat(tracker.passedCount()).isZero();
    }

    @Test
    void noPreviousPositionSkipsCollisionOnFirstTick(Env env) {
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN), new Pos(0, 60, -10));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 2));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        // previousPosition is null (first tick after teleport) → should not detect ring
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var tracker = new RingTrackerComponent();
        playerEntity.addComponent(tracker);
        playerEntity.addComponent(new ScoreComponent());

        entityManager.addEntity(playerEntity);
        entityManager.update(1.0f / 20.0f);

        assertThat(tracker.passedCount()).isZero();
    }

    @Test
    void systemRequiresCorrectComponents() {
        var entityManager = new EntityManager();
        var system = new RingCollisionSystem(entityManager);
        var required = system.getRequiredComponents();

        assertThat(required).containsExactlyInAnyOrder(
                PlayerRefComponent.class, RingTrackerComponent.class,
                ScoreComponent.class, ElytraFlightComponent.class);
    }
}
