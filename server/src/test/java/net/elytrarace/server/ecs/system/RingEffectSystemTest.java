package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingEffectComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.physics.RingType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@EnvTest
class RingEffectSystemTest {

    private static final float DELTA_TIME = 1.0f / 20.0f;

    private Entity createPlayerEntity() {
        var entity = new Entity();
        entity.addComponent(new RingEffectComponent());
        entity.addComponent(new ElytraFlightComponent());
        entity.addComponent(new RingTrackerComponent());
        entity.addComponent(new ScoreComponent());
        return entity;
    }

    private EntityManager createEntityManager() {
        var em = new EntityManager();
        em.addSystem(new RingEffectSystem());
        return em;
    }

    @Test
    void boostMultipliesVelocity() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(0, 0, 10));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.BOOST, 1);

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(flight.getVelocity().x()).isCloseTo(0, within(0.001));
        assertThat(flight.getVelocity().y()).isCloseTo(0, within(0.001));
        assertThat(flight.getVelocity().z()).isCloseTo(15.0, within(0.001));
    }

    @Test
    void slowReducesVelocity() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(0, 0, 10));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.SLOW, 1);

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(flight.getVelocity().x()).isCloseTo(0, within(0.001));
        assertThat(flight.getVelocity().y()).isCloseTo(0, within(0.001));
        assertThat(flight.getVelocity().z()).isCloseTo(5.0, within(0.001));
    }

    @Test
    void boostWithDiagonalVelocity() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(2, 3, 4));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.BOOST, 1);

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(flight.getVelocity().x()).isCloseTo(3.0, within(0.001));
        assertThat(flight.getVelocity().y()).isCloseTo(4.5, within(0.001));
        assertThat(flight.getVelocity().z()).isCloseTo(6.0, within(0.001));
    }

    @Test
    void standardRingHasNoVelocityEffect() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(0, 0, 10));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.STANDARD, 1);

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(flight.getVelocity().z()).isCloseTo(10.0, within(0.001));
    }

    @Test
    void bonusRingHasNoVelocityEffect() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(0, 0, 10));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.BONUS, 1);

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(flight.getVelocity().z()).isCloseTo(10.0, within(0.001));
    }

    @Test
    void checkpointRingHasNoVelocityEffect() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(0, 0, 10));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.CHECKPOINT, 1);

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(flight.getVelocity().z()).isCloseTo(10.0, within(0.001));
    }

    @Test
    void multipleEffectsAppliedInOrder() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(0, 0, 10));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.BOOST, 1); // 10 * 1.5 = 15
        effects.addEffect(RingType.SLOW, 1);  // 15 * 0.5 = 7.5

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(flight.getVelocity().z()).isCloseTo(7.5, within(0.001));
    }

    @Test
    void effectsQueueIsClearedAfterProcessing() {
        var em = createEntityManager();
        var entity = createPlayerEntity();

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.BOOST, 1);

        var flight = entity.getComponent(ElytraFlightComponent.class);
        flight.setVelocity(new Vec(0, 0, 10));

        em.addEntity(entity);
        em.update(DELTA_TIME);

        assertThat(effects.pendingCount()).isZero();
    }

    @Test
    void systemRequiresCorrectComponents() {
        var system = new RingEffectSystem();
        var required = system.getRequiredComponents();

        assertThat(required).containsExactlyInAnyOrder(
                RingEffectComponent.class,
                ElytraFlightComponent.class,
                RingTrackerComponent.class,
                ScoreComponent.class
        );
    }

    @Test
    @org.junit.jupiter.api.DisplayName("BOOST ring sends updated velocity to Minestom player when PlayerRefComponent is present")
    void boostRingSendsVelocityToPlayer(Env env) {
        // Given
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(0, 0, 10));
        entity.addComponent(flight);
        entity.addComponent(new RingEffectComponent());
        entity.addComponent(new RingTrackerComponent());
        entity.addComponent(new ScoreComponent());
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.BOOST, 1);

        var em = new EntityManager();
        em.addSystem(new RingEffectSystem());
        em.addEntity(entity);

        // When
        em.update(1.0f / 20.0f);

        // Then — ECS velocity is multiplied by BOOST_MULTIPLIER (1.5)
        assertThat(flight.getVelocity().z()).isCloseTo(15.0, within(0.001));
        // And — the Minestom player receives the velocity (blocks/tick * 20 = blocks/sec)
        assertThat(player.getVelocity().z()).isCloseTo(300.0, within(0.01));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SLOW ring sends updated velocity to Minestom player when PlayerRefComponent is present")
    void slowRingSendsVelocityToPlayer(Env env) {
        // Given
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(true);
        flight.setVelocity(new Vec(0, 0, 10));
        entity.addComponent(flight);
        entity.addComponent(new RingEffectComponent());
        entity.addComponent(new RingTrackerComponent());
        entity.addComponent(new ScoreComponent());
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));

        var effects = entity.getComponent(RingEffectComponent.class);
        effects.addEffect(RingType.SLOW, 1);

        var em = new EntityManager();
        em.addSystem(new RingEffectSystem());
        em.addEntity(entity);

        // When
        em.update(1.0f / 20.0f);

        // Then — ECS velocity is halved by SLOW_MULTIPLIER (0.5)
        assertThat(flight.getVelocity().z()).isCloseTo(5.0, within(0.001));
        // And — the Minestom player receives the velocity (blocks/tick * 20 = blocks/sec)
        assertThat(player.getVelocity().z()).isCloseTo(100.0, within(0.01));
    }
}
