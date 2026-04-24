package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class ElytraPhysicsSystemTest {

    @Test
    void velocityIsUpdatedWhenFlying(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(0, 0, 0.5));
        entity.addComponent(flight);
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var system = new ElytraPhysicsSystem();
        system.process(entity, 1.0f / 20.0f);

        // Velocity should have changed from the initial value due to physics
        assertThat(flight.getVelocity()).isNotEqualTo(new Vec(0, 0, 0.5));
        // Y should be affected by gravity
        assertThat(flight.getVelocity().y()).isLessThan(0.0);
    }

    @Test
    void notFlyingSkipsPhysics(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(false);
        flight.setVelocity(new Vec(0, 0, 0.5));
        entity.addComponent(flight);
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var system = new ElytraPhysicsSystem();
        system.process(entity, 1.0f / 20.0f);

        // Velocity should not have changed since player is not flying
        assertThat(flight.getVelocity()).isEqualTo(new Vec(0, 0, 0.5));
    }

    @Test
    void systemRequiresCorrectComponents() {
        var system = new ElytraPhysicsSystem();
        var required = system.getRequiredComponents();

        assertThat(required).containsExactlyInAnyOrder(
                ElytraFlightComponent.class, PlayerRefComponent.class);
    }

    @Test
    void entityManagerOnlyProcessesMatchingEntities(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entityManager = new EntityManager();
        entityManager.addSystem(new ElytraPhysicsSystem());

        // Entity with all required components
        var playerEntity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(0, 0, 1.0));
        playerEntity.addComponent(flight);
        playerEntity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        entityManager.addEntity(playerEntity);

        // Entity missing PlayerRefComponent -- should not be processed
        var incompleteEntity = new Entity();
        var flight2 = new ElytraFlightComponent();
        flight2.setFlying(true);
        flight2.setVelocity(new Vec(0, 0, 1.0));
        incompleteEntity.addComponent(flight2);
        entityManager.addEntity(incompleteEntity);

        entityManager.update(1.0f / 20.0f);

        // Player entity should have been updated
        assertThat(flight.getVelocity()).isNotEqualTo(new Vec(0, 0, 1.0));
        // Incomplete entity should remain unchanged
        assertThat(flight2.getVelocity()).isEqualTo(new Vec(0, 0, 1.0));
    }
}
