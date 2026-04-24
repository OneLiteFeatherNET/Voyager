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
import static org.assertj.core.api.Assertions.within;

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

    // ── Activation ────────────────────────────────────────────────────────────

    @Test
    void boostActivationAppliesVanillaFormulaImmediately(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        // From rest (vel = 0): vanilla gives 0 * 0.5 + 0.85 * look = 0.85 b/t
        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        assertThat(flight.getVelocity().length()).isCloseTo(0.85, within(1e-9));
        assertThat(boost.isBurning()).isTrue();
        assertThat(boost.isOnCooldown()).isTrue();
        // Activation tick also counts as the first burn tick
        assertThat(boost.getBurnTicksRemaining()).isEqualTo(BoostConfig.DEFAULT.burnDurationTicks() - 1);
    }

    @Test
    void boostWithExistingVelocityBlendsPreviousSpeed(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        // Player is already moving south at 1 b/t (same direction as default look yaw=0 → south)
        Vec initial = new Vec(0.0, 0.0, 1.0);
        flight.setVelocity(initial);

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        // Vanilla: 0.5*(0,0,1) + 0.85*(0,0,1) = (0,0,1.35) → length 1.35 > 1.0
        assertThat(flight.getVelocity().length()).isGreaterThan(initial.length());
        assertThat(boost.isBurning()).isTrue();
        assertThat(boost.isOnCooldown()).isTrue();
    }

    @Test
    void boostIgnoredWhenNotFlying(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
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
    void boostIgnoredWhenOnCooldown(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        boost.startCooldown();
        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        assertThat(flight.getVelocity().length()).isEqualTo(0.0);
    }

    @Test
    void noOpWhenNoRequest(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var flight = entity.getComponent(ElytraFlightComponent.class);

        new FireworkBoostSystem().process(entity, TICK);

        assertThat(flight.getVelocity().length()).isEqualTo(0.0);
    }

    // ── Sustained burn ────────────────────────────────────────────────────────

    @Test
    void vanillaFormulaConvergesEachBurnTick(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var system = new FireworkBoostSystem();

        // Tick 1: activate + first formula application (0 → 0.85 b/t)
        boost.requestBoost();
        system.process(entity, TICK);
        double afterTick1 = flight.getVelocity().length();
        assertThat(boost.getBurnTicksRemaining()).isEqualTo(BoostConfig.DEFAULT.burnDurationTicks() - 1);

        // Tick 2: formula continues — speed increases toward ~1.7 b/t steady state
        system.process(entity, TICK);
        assertThat(flight.getVelocity().length()).isGreaterThan(afterTick1);
        assertThat(boost.getBurnTicksRemaining()).isEqualTo(BoostConfig.DEFAULT.burnDurationTicks() - 2);
    }

    @Test
    void burnCancelledWhenPlayerLands(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        var entity = buildEntity(player, true);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var system = new FireworkBoostSystem();

        boost.requestBoost();
        system.process(entity, TICK);
        assertThat(boost.isBurning()).isTrue();

        flight.setFlying(false);
        system.process(entity, TICK);

        assertThat(boost.isBurning()).isFalse();
    }

    @Test
    void velocityIsCappedAtMaxSpeed(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        // Low cap so an already-fast player gets clamped
        var cfg = new BoostConfig(5, 0.5, 4_000);
        var entity = buildEntity(player, true, cfg);
        var flight = entity.getComponent(ElytraFlightComponent.class);
        var boost = entity.getComponent(FireworkBoostComponent.class);

        // Pre-load velocity above the cap so clamping is guaranteed to trigger
        flight.setVelocity(new Vec(0.0, 0.0, 2.0));

        boost.requestBoost();
        new FireworkBoostSystem().process(entity, TICK);

        assertThat(flight.getVelocity().length()).isLessThanOrEqualTo(cfg.maxSpeedBlocksPerTick() + 1e-9);
    }

    @Test
    void burnEndsAfterConfiguredDuration(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        var player = env.createPlayer(instance, new Pos(0, 60, 0));

        // 3-tick burn: activation tick is the first burn tick, so 3 process() calls exhaust it
        var cfg = new BoostConfig(3, 2.75, 4_000);
        var entity = buildEntity(player, true, cfg);
        var boost = entity.getComponent(FireworkBoostComponent.class);
        var system = new FireworkBoostSystem();

        boost.requestBoost();
        system.process(entity, TICK); // activate + burn tick 1 (3→2)
        system.process(entity, TICK); // burn tick 2 (2→1)
        system.process(entity, TICK); // burn tick 3 (1→0)

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
