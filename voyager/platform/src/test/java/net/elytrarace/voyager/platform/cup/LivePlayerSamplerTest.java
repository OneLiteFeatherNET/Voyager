package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.tick.FlightSample;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seam between a live player and the simulation's input, tested where it carries a decision: the
 * firework boost.
 *
 * <p><strong>Two racers, always, and never in the same state.</strong> One boosts and the other does
 * not, because a sampler that reported a single global burn against every sample would satisfy any
 * one-player assertion — and "the flag was reported for the wrong player" is the mutation that costs
 * the most downstream, since both simulations then stay internally consistent while being wrong.
 */
@EnvTest
class LivePlayerSamplerTest {

    /** 5 and 12: distinct, not multiples, and neither is the Vanilla-derived default of 30. */
    private static final BoostConfig BOOST = new BoostConfig(5, 12);

    @Test
    void reportsNoBoostForARacerWhoHasNotUsedARocket(Env env) {
        Instance instance = flatInstance(env);
        Player ada = env.createPlayer(instance, new Pos(0.5, 60, 0.5));
        FireworkBoostTracker boosts = new FireworkBoostTracker();

        FlightSample sample = sampleOf(new LivePlayerSampler(() -> List.of(ada), boosts), ada);

        assertThat(sample.input().fireworkBoostActive()).isFalse();
        assertThat(sample.input().fireworkTicksRemaining()).isZero();
    }

    /**
     * The burn reaches the simulation as the boolean {@code ElytraSimulator} applies its impulse on,
     * and as the countdown the trace format carries — and it reaches the racer it belongs to and
     * nobody else.
     */
    @Test
    void reportsTheBurnAgainstTheRacerItBelongsToAndNotTheOtherOne(Env env) {
        Instance instance = flatInstance(env);
        Player ada = env.createPlayer(instance, new Pos(0.5, 60, 0.5));
        Player ben = env.createPlayer(instance, new Pos(2.5, 60, 2.5));
        ada.setFlyingWithElytra(true);
        ben.setFlyingWithElytra(true);
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        LivePlayerSampler sampler = new LivePlayerSampler(() -> List.of(ada, ben), boosts);

        assertThat(boosts.requestBoost(ben.getUuid(), BOOST, true)).isTrue();

        List<FlightSample> samples = sampler.sampleAtTickBoundary();
        assertThat(samples).hasSize(2);
        assertThat(inputOf(samples, ada.getUuid()).fireworkBoostActive())
                .describedAs("Ada did not boost")
                .isFalse();
        assertThat(inputOf(samples, ben.getUuid()).fireworkBoostActive()).isTrue();
        assertThat(inputOf(samples, ben.getUuid()).fireworkTicksRemaining())
                .describedAs("the remaining count, not merely a flag")
                .isEqualTo(BOOST.burnDurationTicks());
        assertThat(inputOf(samples, ada.getUuid()).fireworkTicksRemaining()).isZero();
    }

    /**
     * The countdown the sampler reports is the tracker's, tick by tick, rather than a value latched
     * when the burn began. A sampler that read the count once would keep reporting 5 for as long as
     * the burn ran, which is the shape a trace consumer checking the flag against the countdown is
     * built to notice.
     */
    @Test
    void theReportedCountdownFollowsTheBurnDownAndStopsWithIt(Env env) {
        Instance instance = flatInstance(env);
        Player ada = env.createPlayer(instance, new Pos(0.5, 60, 0.5));
        ada.setFlyingWithElytra(true);
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        LivePlayerSampler sampler = new LivePlayerSampler(() -> List.of(ada), boosts);
        boosts.requestBoost(ada.getUuid(), BOOST, true);

        assertThat(sampleOf(sampler, ada).input().fireworkTicksRemaining()).isEqualTo(5);
        boosts.advance();
        assertThat(sampleOf(sampler, ada).input().fireworkTicksRemaining()).isEqualTo(4);
        boosts.advance();
        boosts.advance();
        boosts.advance();
        assertThat(sampleOf(sampler, ada).input().fireworkTicksRemaining()).isEqualTo(1);
        boosts.advance();
        assertThat(sampleOf(sampler, ada).input().fireworkBoostActive()).isFalse();
        assertThat(sampleOf(sampler, ada).input().fireworkTicksRemaining()).isZero();
    }

    private static Instance flatInstance(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        return instance;
    }

    private static FlightSample sampleOf(LivePlayerSampler sampler, Player player) {
        List<FlightSample> samples = sampler.sampleAtTickBoundary();
        return byPlayer(samples, player.getUuid());
    }

    private static net.elytrarace.voyager.api.physics.FlightInput inputOf(
            Collection<FlightSample> samples, UUID playerId) {
        return byPlayer(samples, playerId).input();
    }

    private static FlightSample byPlayer(Collection<FlightSample> samples, UUID playerId) {
        Optional<FlightSample> found = samples.stream()
                .filter(sample -> sample.playerId().equals(playerId))
                .findFirst();
        assertThat(found).describedAs("a sample for %s", playerId).isPresent();
        return found.orElseThrow();
    }
}
