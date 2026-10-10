package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.player.PlayerServiceImpl;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
@org.junit.jupiter.api.Disabled("Requires full Minestom player support - run manually")
class OutOfBoundsSystemTest {

    private static final Ring TEST_RING = new Ring(new Vec(0, 60, 10), new Vec(0, 0, 1), 5.0, 10);
    private static final Pos SPAWN = new Pos(0, 64, 0);

    private EntityManager setupEntityManager(Env env, InstanceContainer instance) {
        var entityManager = new EntityManager();

        var gameEntity = new Entity();
        var activeMap = new ActiveMapComponent();
        activeMap.setCurrentMap(new MapDefinition("TestMap", Path.of("/tmp/test"),
                List.of(TEST_RING), SPAWN));
        gameEntity.addComponent(activeMap);
        entityManager.addEntity(gameEntity);

        var playerService = new PlayerServiceImpl(instance);
        entityManager.addSystem(new OutOfBoundsSystem(entityManager, playerService));
        return entityManager;
    }

    @Test
    void systemRequiresCorrectComponents() {
        var em = new EntityManager();
        var system = new OutOfBoundsSystem(em, null);

        assertThat(system.getRequiredComponents())
                .containsExactlyInAnyOrder(PlayerRefComponent.class, ElytraFlightComponent.class);
    }

    @Test
    void constantsHaveExpectedValues() {
        assertThat(OutOfBoundsSystem.MIN_Y).isEqualTo(-64.0);
        assertThat(OutOfBoundsSystem.MAX_Y).isEqualTo(320.0);
        assertThat(OutOfBoundsSystem.RESET_COOLDOWN_TICKS).isEqualTo(40);
        assertThat(OutOfBoundsSystem.MIN_AIR_TICKS_BEFORE_LAND_RESET).isEqualTo(20);
    }

    @Test
    void notFlyingSkipsAllChecks(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var em = setupEntityManager(env, instance);
        // Player positioned below void floor
        var player = env.createPlayer(instance, new Pos(0, -100, 0));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(false); // Not flying — should skip all checks
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        em.addEntity(playerEntity);

        em.update(1.0f / 20.0f);

        // If not flying, no reset is triggered — flight state unchanged
        assertThat(flight.isFlying()).isFalse();
    }

    @Test
    void outOfBoundsResetsVelocityImmediately(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var em = setupEntityManager(env, instance);
        // Player clearly below MIN_Y
        var player = env.createPlayer(instance, new Pos(0, -200, 0));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(1, -5, 2));
        flight.setPreviousPosition(new Pos(0, -195, 0));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        em.addEntity(playerEntity);

        em.update(1.0f / 20.0f);

        // Velocity and previous position must be cleared on reset
        assertThat(flight.getVelocity()).isEqualTo(Vec.ZERO);
        assertThat(flight.getPreviousPosition()).isNull();
    }

    @Test
    void aboveCeilingResetsVelocityImmediately(Env env) {
        var instance = (InstanceContainer) env.createFlatInstance();
        var em = setupEntityManager(env, instance);
        // Player clearly above MAX_Y
        var player = env.createPlayer(instance, new Pos(0, 400, 0));

        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(0, 3, 0));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        em.addEntity(playerEntity);

        em.update(1.0f / 20.0f);

        assertThat(flight.getVelocity()).isEqualTo(Vec.ZERO);
        assertThat(flight.getPreviousPosition()).isNull();
    }
}
