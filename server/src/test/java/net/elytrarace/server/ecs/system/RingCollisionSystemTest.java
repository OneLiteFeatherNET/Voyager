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
        // Player positioned so that prevPos (pos - velocity) is behind the ring
        // and currentPos is past the ring
        var player = env.createPlayer(instance, new Pos(0, 60, 2));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        // Velocity of 4 blocks/tick along Z means prevPos = (0, 60, -2)
        flight.setVelocity(new Vec(0, 0, 4));
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
        flight.setVelocity(new Vec(0, 0, 4));
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
    void multipleRingsCanBePassedInOneTick(Env env) {
        // Two rings close together, both passed in a single high-velocity tick
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN, RING_AT_Z10), new Pos(0, 60, -20));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        // Player at z=15, velocity=30 means prevPos at z=-15 -- passes both rings
        var player = env.createPlayer(instance, new Pos(0, 60, 15));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(0, 0, 30));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var tracker = new RingTrackerComponent();
        playerEntity.addComponent(tracker);

        var score = new ScoreComponent();
        playerEntity.addComponent(score);

        entityManager.addEntity(playerEntity);
        entityManager.update(1.0f / 20.0f);

        assertThat(tracker.hasPassed(0)).isTrue();
        assertThat(tracker.hasPassed(1)).isTrue();
        assertThat(score.getRingPoints()).isEqualTo(35); // 10 + 25
    }

    @Test
    void missedRingDoesNotUpdateScore(Env env) {
        var map = new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(RING_AT_ORIGIN), new Pos(0, 60, -10));
        var entityManager = setupEntityManager(map);

        var instance = env.createFlatInstance();
        // Player far away from the ring
        var player = env.createPlayer(instance, new Pos(100, 60, 2));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(0, 0, 4));
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
        flight.setVelocity(new Vec(0, 0, 4));
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
    void systemRequiresCorrectComponents() {
        var entityManager = new EntityManager();
        var system = new RingCollisionSystem(entityManager);
        var required = system.getRequiredComponents();

        assertThat(required).containsExactlyInAnyOrder(
                PlayerRefComponent.class, RingTrackerComponent.class,
                ScoreComponent.class, ElytraFlightComponent.class);
    }
}
