package net.elytrarace.server.ecs.component;

import net.elytrarace.server.cup.BoostConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FireworkBoostComponentTest {

    @Test
    void noBoostRequestedByDefault() {
        var comp = new FireworkBoostComponent();
        assertThat(comp.claimBoostRequest()).isFalse();
    }

    @Test
    void requestBoostIsClaimedOnce() {
        var comp = new FireworkBoostComponent();
        comp.requestBoost();

        assertThat(comp.claimBoostRequest()).isTrue();
        assertThat(comp.claimBoostRequest()).isFalse();
    }

    @Test
    void notOnCooldownByDefault() {
        var comp = new FireworkBoostComponent();
        assertThat(comp.isOnCooldown()).isFalse();
    }

    @Test
    void startCooldownActivatesCooldown() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(0.5, 24, 0.035, 2.75, 4_000));
        comp.startCooldown();

        assertThat(comp.isOnCooldown()).isTrue();
    }

    @Test
    void getCooldownRemainingTicksApproximatesConfiguredDuration() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(0.5, 24, 0.035, 2.75, 2_000)); // 2 s = 40 ticks
        comp.startCooldown();

        assertThat(comp.getCooldownRemainingTicks()).isCloseTo(40, within(2));
    }

    @Test
    void cooldownExpiresAfterConfiguredDuration() throws InterruptedException {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(0.5, 24, 0.035, 2.75, 100));
        comp.startCooldown();

        assertThat(comp.isOnCooldown()).isTrue();
        Thread.sleep(150);
        assertThat(comp.isOnCooldown()).isFalse();
    }

    // ── Burn state ─────────────────────────────────────────────────────────────

    @Test
    void notBurningByDefault() {
        var comp = new FireworkBoostComponent();
        assertThat(comp.isBurning()).isFalse();
    }

    @Test
    void startBurnActivatesBurn() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(0.5, 24, 0.035, 2.75, 4_000));
        comp.startBurn();

        assertThat(comp.isBurning()).isTrue();
        assertThat(comp.getBurnTicksRemaining()).isEqualTo(24);
    }

    @Test
    void tickBurnDecrementsToZero() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(0.5, 3, 0.035, 2.75, 4_000));
        comp.startBurn();

        comp.tickBurn();
        assertThat(comp.getBurnTicksRemaining()).isEqualTo(2);

        comp.tickBurn();
        comp.tickBurn();
        assertThat(comp.isBurning()).isFalse();
    }

    @Test
    void tickBurnDoesNotGoBelowZero() {
        var comp = new FireworkBoostComponent();
        comp.tickBurn();
        comp.tickBurn();

        assertThat(comp.getBurnTicksRemaining()).isEqualTo(0);
    }

    @Test
    void cancelBurnStopsBurn() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(0.5, 24, 0.035, 2.75, 4_000));
        comp.startBurn();

        comp.cancelBurn();

        assertThat(comp.isBurning()).isFalse();
        assertThat(comp.getBurnTicksRemaining()).isEqualTo(0);
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    @Test
    void defaultConfigIsApplied() {
        var comp = new FireworkBoostComponent();
        assertThat(comp.getBoostConfig()).isEqualTo(BoostConfig.DEFAULT);
    }

    @Test
    void boostConfigCanBeReplaced() {
        var comp = new FireworkBoostComponent();
        var custom = new BoostConfig(1.0, 30, 0.05, 3.0, 8_000);
        comp.setBoostConfig(custom);

        assertThat(comp.getBoostConfig()).isEqualTo(custom);
    }

    @Test
    void concurrentRequestAndClaim() throws InterruptedException {
        var comp = new FireworkBoostComponent();
        Thread requester = new Thread(comp::requestBoost);
        requester.start();
        requester.join();

        assertThat(comp.claimBoostRequest()).isTrue();
        assertThat(comp.claimBoostRequest()).isFalse();
    }
}
