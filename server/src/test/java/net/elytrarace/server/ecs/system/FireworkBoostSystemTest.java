package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.cup.BoostConfig;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.FireworkBoostComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class FireworkBoostSystemTest {

    private static final float TICK = 1.0f / 20.0f;

    @Test
    void systemRequiresBoostFlightAndRef() {
        var system = new FireworkBoostSystem();
        assertThat(system.getRequiredComponents()).containsExactlyInAnyOrder(
                FireworkBoostComponent.class,
                ElytraFlightComponent.class,
                PlayerRefComponent.class
        );
    }

    @Test
    void boostAppliedWhenFlyingAndNoCooldown(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        // Velocity must have changed from zero
        assertThat(flight.getVelocity().length()).isGreaterThan(0.0);
        // Cooldown must have started
        assertThat(boost.isOnCooldown()).isTrue();
        // Request must have been consumed
        assertThat(boost.claimBoostRequest()).isFalse();
    }

    @Test
    void boostIgnoredWhenNotFlying(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, false);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        assertThat(flight.getVelocity().length()).isEqualTo(0.0);
        assertThat(boost.isOnCooldown()).isFalse();
    }

    @Test
    void boostIgnoredWhenOnCooldown(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);

        // Simulate a previous boost that started the cooldown
        boost.startCooldown();

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        // Cooldown was active — no impulse, no second cooldown reset
        assertThat(boost.getCooldownRemainingTicks())
                .isLessThan((int) (BoostConfig.DEFAULT.cooldownMs() / 50L));
    }

    @Test
    void cooldownCountsDownEachTick(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var system = new FireworkBoostSystem();

        // Apply boost to start cooldown
        boost.requestBoost();
        system.process(entity, TICK);
        int initial = boost.getCooldownRemainingTicks();

        // One tick later, without a new request
        system.process(entity, TICK);
        assertThat(boost.getCooldownRemainingTicks()).isEqualTo(initial - 1);
    }

    @Test
    void noOpWhenNoRequest(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        new FireworkBoostSystem().process(entity, TICK);

        assertThat(flight.getVelocity().length()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------

    private static Entity buildEntity(net.minestom.server.entity.Player player, boolean flying) {
        var entity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(flying);
        entity.addComponent(flight);
        entity.addComponent(new FireworkBoostComponent());
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        return entity;
    }
}
