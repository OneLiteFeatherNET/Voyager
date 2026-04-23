package net.elytrarace.server.ecs.component;

import net.elytrarace.server.cup.BoostConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void startCooldownSetsTicksFromConfig() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(2.5, 2_000)); // 2000 ms / 50 ms = 40 ticks

        comp.startCooldown();

        assertThat(comp.getCooldownRemainingTicks()).isEqualTo(40);
        assertThat(comp.isOnCooldown()).isTrue();
    }

    @Test
    void tickCooldownDecrementsToZero() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(2.5, 100)); // 2 ticks
        comp.startCooldown();

        comp.tickCooldown();
        assertThat(comp.isOnCooldown()).isTrue();

        comp.tickCooldown();
        assertThat(comp.isOnCooldown()).isFalse();
    }

    @Test
    void tickCooldownDoesNotGoBelowZero() {
        var comp = new FireworkBoostComponent();
        comp.tickCooldown();
        comp.tickCooldown();

        assertThat(comp.getCooldownRemainingTicks()).isEqualTo(0);
    }

    @Test
    void defaultConfigIsApplied() {
        var comp = new FireworkBoostComponent();
        assertThat(comp.getBoostConfig()).isEqualTo(BoostConfig.DEFAULT);
    }

    @Test
    void boostConfigCanBeReplaced() {
        var comp = new FireworkBoostComponent();
        var custom = new BoostConfig(5.0, 8_000);
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
