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
        comp.setBoostConfig(new BoostConfig(2.5, 4_000));

        comp.startCooldown();

        assertThat(comp.isOnCooldown()).isTrue();
    }

    @Test
    void getCooldownRemainingTicksApproximatesConfiguredDuration() {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(2.5, 2_000)); // 2000 ms = 40 ticks

        comp.startCooldown();

        // Allow ±2 ticks of measurement slack (wall-clock call overhead)
        assertThat(comp.getCooldownRemainingTicks()).isCloseTo(40, within(2));
    }

    @Test
    void getCooldownRemainingTicksReturnsZeroAfterExpiry() throws InterruptedException {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(2.5, 50)); // 50 ms = 1 tick

        comp.startCooldown();
        Thread.sleep(100); // wait for cooldown to expire

        assertThat(comp.getCooldownRemainingTicks()).isEqualTo(0);
        assertThat(comp.isOnCooldown()).isFalse();
    }

    @Test
    void cooldownExpiresAfterConfiguredDuration() throws InterruptedException {
        var comp = new FireworkBoostComponent();
        comp.setBoostConfig(new BoostConfig(2.5, 100)); // 100 ms

        comp.startCooldown();
        assertThat(comp.isOnCooldown()).isTrue();

        Thread.sleep(150);
        assertThat(comp.isOnCooldown()).isFalse();
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
