package net.elytrarace.server.ecs.system;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.server.cup.BoostConfig;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.FireworkBoostComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
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

    // ── Phase 1: kick ──────────────────────────────────────────────────────────

    @Test
    void kickIsAdditiveToExistingVelocity(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        // Give the player an existing lateral velocity
        Vec initial = new Vec(1.0, 0.0, 0.0);
        flight.setVelocity(initial);

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        // Velocity must have INCREASED (kick is additive), not replaced
        assertThat(flight.getVelocity().length()).isGreaterThan(initial.length());
        // Burn must have started
        assertThat(boost.isBurning()).isTrue();
        // Cooldown must have started
        assertThat(boost.isOnCooldown()).isTrue();
    }

    @Test
    void kickIgnoredWhenNotFlying(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, false);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        assertThat(flight.getVelocity().length()).isEqualTo(0.0);
        assertThat(boost.isBurning()).isFalse();
        assertThat(boost.isOnCooldown()).isFalse();
    }

    @Test
    void kickIgnoredWhenOnCooldown(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        boost.startCooldown(); // simulate a previous boost
        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        // No kick applied
        assertThat(flight.getVelocity().length()).isEqualTo(0.0);
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

    // ── Phase 2: burn ──────────────────────────────────────────────────────────

    @Test
    void sustainedThrustIsAppliedEachBurnTick(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var system = new FireworkBoostSystem();

        // Activate boost
        boost.requestBoost();
        system.process(entity, TICK);
        double afterKick = flight.getVelocity().length();

        // Next tick: burn applies thrust, adding more speed
        system.process(entity, TICK);
        assertThat(flight.getVelocity().length()).isGreaterThan(afterKick);
        assertThat(boost.getBurnTicksRemaining()).isEqualTo(BoostConfig.DEFAULT.burnDurationTicks() - 1);
    }

    @Test
    void burnCancelledWhenPlayerLands(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var system = new FireworkBoostSystem();

        // Activate boost
        boost.requestBoost();
        system.process(entity, TICK);
        assertThat(boost.isBurning()).isTrue();

        // Simulate landing
        flight.setFlying(false);
        system.process(entity, TICK);

        assertThat(boost.isBurning()).isFalse();
    }

    @Test
    void velocityIsCappedAtMaxSpeed(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        // Config with very high kick and low cap
        var cfg = new BoostConfig(10.0, 5, 0.035, 3.0, 4_000);
        var entity = buildEntity(player, true, cfg);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var boost = entity.getComponent(FireworkBoostComponent.class);

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        // Velocity magnitude must not exceed the cap
        assertThat(flight.getVelocity().length()).isLessThanOrEqualTo(cfg.maxSpeedBlocksPerTick() + 1e-9);
    }

    @Test
    void burnEndsAfterConfiguredDuration(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var cfg = new BoostConfig(0.5, 3, 0.035, 2.75, 4_000);
        var entity = buildEntity(player, true, cfg);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var system = new FireworkBoostSystem();

        boost.requestBoost();
        system.process(entity, TICK); // activation tick (burn starts at 3)

        system.process(entity, TICK); // burn tick 1 (→ 2)
        system.process(entity, TICK); // burn tick 2 (→ 1)
        system.process(entity, TICK); // burn tick 3 (→ 0)

        assertThat(boost.isBurning()).isFalse();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Entity buildEntity(net.minestom.server.entity.Player player, boolean flying) {
        return buildEntity(player, flying, BoostConfig.DEFAULT);
    }

    private static Entity buildEntity(net.minestom.server.entity.Player player, boolean flying, BoostConfig cfg) {
        var entity = new Entity();
        var flight = new ElytraFlightComponent();
        flight.setFlying(flying);
        entity.addComponent(flight);
        var boost = new FireworkBoostComponent();
        boost.setBoostConfig(cfg);
        entity.addComponent(boost);
        entity.addComponent(new PlayerRefComponent(player.getUuid(), player));
        return entity;
    }
}
