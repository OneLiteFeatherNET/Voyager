package net.elytrarace.server.game;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.game.mode.GameMode;
import net.elytrarace.server.cup.CupDefinition;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.CupProgressComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.ecs.system.ElytraPhysicsSystem;
import net.elytrarace.server.ecs.system.RingCollisionSystem;
import net.elytrarace.server.ecs.system.ScoreDisplaySystem;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.player.PlayerEventHandler;
import net.elytrarace.server.player.PlayerServiceImpl;
import net.elytrarace.server.world.MapInstanceService;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnvTest
@org.junit.jupiter.api.Disabled("Requires full Minestom player support - run manually")
class GameOrchestratorTest {

    private static final Ring TEST_RING = new Ring(new Vec(0, 50, 50), new Vec(0, 0, 1), 5.0, 10);

    private static CupDefinition createTestCup() {
        var map1 = new MapDefinition("Map1", Path.of("/tmp/map1"), List.of(TEST_RING), new Pos(0, 60, 0));
        var map2 = new MapDefinition("Map2", Path.of("/tmp/map2"), List.of(TEST_RING), new Pos(0, 60, 0));
        return new CupDefinition("TestCup", GameMode.RACE, List.of(map1, map2));
    }

    @Test
    void startGameCreatesGameEntity(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());

        Entity gameEntity = orchestrator.getGameEntity();
        assertThat(gameEntity).isNotNull();
        assertThat(gameEntity.hasComponent(CupProgressComponent.class)).isTrue();
        assertThat(gameEntity.hasComponent(ActiveMapComponent.class)).isTrue();
    }

    @Test
    void startGameRegistersSystems(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());

        var systems = orchestrator.getEntityManager().getSystems();
        assertThat(systems).hasSize(3);
        assertThat(systems).hasAtLeastOneElementOfType(ElytraPhysicsSystem.class);
        assertThat(systems).hasAtLeastOneElementOfType(RingCollisionSystem.class);
        assertThat(systems).hasAtLeastOneElementOfType(ScoreDisplaySystem.class);
    }

    @Test
    void startGameCreatesPlayerEntities(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 42, 0));
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());

        // Game entity + 1 player entity
        var entities = orchestrator.getEntityManager().getEntities();
        assertThat(entities).hasSizeGreaterThanOrEqualTo(2);

        // Find the player entity and verify its components
        var playerEntities = orchestrator.getEntityManager()
                .getEntitiesWithComponent(PlayerRefComponent.class);
        assertThat(playerEntities).hasSize(1);

        Entity playerEntity = playerEntities.iterator().next();
        assertThat(playerEntity.hasComponent(ElytraFlightComponent.class)).isTrue();
        assertThat(playerEntity.hasComponent(RingTrackerComponent.class)).isTrue();
        assertThat(playerEntity.hasComponent(ScoreComponent.class)).isTrue();

        var ref = playerEntity.getComponent(PlayerRefComponent.class);
        assertThat(ref.getPlayerId()).isEqualTo(player.getUuid());
    }

    @Test
    void startGameStartsPhaseSeries(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());

        assertThat(orchestrator.getPhaseSeries()).isNotNull();
        assertThat(orchestrator.getPhaseSeries().isRunning()).isTrue();
    }

    @Test
    void cupProgressStartsAtFirstMap(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());

        var progress = orchestrator.getGameEntity().getComponent(CupProgressComponent.class);
        assertThat(progress.getCurrentMapIndex()).isZero();
        assertThat(progress.isComplete()).isFalse();
        assertThat(progress.getCurrentMap().name()).isEqualTo("Map1");
        assertThat(progress.totalMaps()).isEqualTo(2);
    }

    @Test
    void loadNextMapSetsActiveMapComponent(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());
        orchestrator.loadNextMap().join();

        var activeMap = orchestrator.getGameEntity().getComponent(ActiveMapComponent.class);
        assertThat(activeMap.getCurrentMap()).isNotNull();
        assertThat(activeMap.getCurrentMap().name()).isEqualTo("Map1");
        assertThat(activeMap.getMapInstance()).isNotNull();
    }

    @Test
    void loadNextMapThrowsWhenNoGameRunning(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        assertThatThrownBy(orchestrator::loadNextMap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No game is currently running");
    }

    @Test
    void advanceToNextMapProgressesCup(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());
        orchestrator.advanceToNextMap().join();

        var progress = orchestrator.getGameEntity().getComponent(CupProgressComponent.class);
        assertThat(progress.getCurrentMapIndex()).isEqualTo(1);
        assertThat(progress.getCurrentMap().name()).isEqualTo("Map2");
    }

    @Test
    void ecsUpdateProcessesEntitiesWithoutError(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var orchestrator = createOrchestrator(env, instance);

        orchestrator.startGame(createTestCup());

        // Running one ECS update tick should not throw
        orchestrator.getEntityManager().update(1.0f / 20.0f);
    }

    private static GameOrchestrator createOrchestrator(Env env, InstanceContainer instance) {
        var playerService = new PlayerServiceImpl(instance);
        var eventHandler = new PlayerEventHandler(playerService, instance);
        return new GameOrchestrator(playerService, stubMapService(env), eventHandler);
    }

    /**
     * Creates a stub MapInstanceService that returns a flat instance from the test env
     * instead of loading from disk.
     */
    private static MapInstanceService stubMapService(Env env) {
        return new MapInstanceService() {
            @Override
            public CompletableFuture<InstanceContainer> loadMap(String mapName, Path worldDirectory) {
                return CompletableFuture.completedFuture((InstanceContainer) env.createFlatInstance());
            }

            @Override
            public void unloadMap(InstanceContainer instance) {
                // no-op for testing
            }

            @Override
            public Collection<InstanceContainer> getLoadedMaps() {
                return List.of();
            }
        };
    }
}
