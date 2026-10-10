package net.elytrarace.voyager.platform.flight;

import net.elytrarace.voyager.api.race.BoostConfig;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The burn is pure arithmetic and is tested as such — no server, no player, no rocket.
 *
 * <h2>What the fixtures are built to tell apart</h2>
 *
 * <p><strong>The two tunings differ on both numbers.</strong> {@link #SHORT} burns 4 and cools down
 * 9; {@link #LONG} burns 7 and cools down 11. Neither burn divides the other, neither cooldown is
 * twice its burn, and no number appears in both — so a tracker that read the burn where it meant the
 * cooldown, or that kept the first configuration it ever saw, produces a count that is in neither
 * column.
 *
 * <p><strong>Two players, never one.</strong> Every per-player assertion is made against a second
 * racer whose state is deliberately different at that moment, because a tracker keyed on nothing at
 * all would pass every single-player test in this file.
 */
class FireworkBoostTrackerTest {

    private static final BoostConfig SHORT = new BoostConfig(4, 9);
    private static final BoostConfig LONG = new BoostConfig(7, 11);

    private static final UUID ADA = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID BEN = UUID.fromString("00000000-0000-4000-8000-0000000000b2");

    private static final boolean GLIDING = true;
    private static final boolean ON_FOOT = false;

    private final FireworkBoostTracker tracker = new FireworkBoostTracker();

    // ------------------------------------------------------------------------------------------
    // The burn
    // ------------------------------------------------------------------------------------------

    @Test
    void aBurnStartsOnTheTickItIsAskedForAndIsReportedAtItsFullLength() {
        assertThat(tracker.burning(ADA)).isFalse();

        assertThat(tracker.requestBoost(ADA, SHORT, GLIDING)).isTrue();

        assertThat(tracker.burning(ADA)).isTrue();
        assertThat(tracker.ticksRemaining(ADA))
                .describedAs("the tick it starts on counts, so the first reading is the whole burn")
                .isEqualTo(4);
    }

    /**
     * The count that the off-by-one would move, and the one mutation this whole class exists for: a
     * burn of 4 must be reported active on exactly 4 samples. Counted by sampling and then advancing,
     * which is the order {@code CupSession.tick()} uses — advancing first would spend a tick before
     * anything observed it and produce 3 here, and every other value in this file would still agree
     * with itself.
     */
    @Test
    void aBurnIsReportedOnExactlyAsManyTicksAsItWasConfiguredFor() {
        tracker.requestBoost(ADA, SHORT, GLIDING);

        int boostedTicks = 0;
        for (int tick = 0; tick < 20; tick++) {
            if (tracker.burning(ADA)) {
                boostedTicks++;
            }
            tracker.advance();
        }

        assertThat(boostedTicks).isEqualTo(SHORT.burnDurationTicks());
    }

    @Test
    void aBurnCountsDownOneTickAtATimeAndThenStops() {
        tracker.requestBoost(ADA, SHORT, GLIDING);

        assertThat(tracker.ticksRemaining(ADA)).isEqualTo(4);
        tracker.advance();
        assertThat(tracker.ticksRemaining(ADA)).isEqualTo(3);
        tracker.advance();
        tracker.advance();
        assertThat(tracker.ticksRemaining(ADA)).isEqualTo(1);
        tracker.advance();
        assertThat(tracker.ticksRemaining(ADA)).isZero();
        assertThat(tracker.burning(ADA)).isFalse();

        tracker.advance();
        assertThat(tracker.ticksRemaining(ADA))
                .describedAs("an ended burn stays ended rather than counting past zero")
                .isZero();
    }

    /**
     * The configuration is read once, at the moment the boost starts. A burn begun under one map's
     * tuning has to run that map's length even if a later request uses another — otherwise a burn of
     * some third length nobody configured becomes possible.
     */
    @Test
    void aBurnRunsTheLengthOfTheConfigurationItWasStartedWith() {
        tracker.requestBoost(ADA, LONG, GLIDING);
        tracker.requestBoost(BEN, SHORT, GLIDING);

        for (int tick = 0; tick < 4; tick++) {
            tracker.advance();
        }

        assertThat(tracker.ticksRemaining(BEN))
                .describedAs("the 4-tick burn is over")
                .isZero();
        assertThat(tracker.ticksRemaining(ADA))
                .describedAs("the 7-tick burn has 3 left, on its own map's tuning")
                .isEqualTo(3);
    }

    // ------------------------------------------------------------------------------------------
    // The cooldown
    // ------------------------------------------------------------------------------------------

    @Test
    void aSecondBoostDuringTheCooldownIsRefusedAndChangesNothing() {
        tracker.requestBoost(ADA, SHORT, GLIDING);
        tracker.advance();

        assertThat(tracker.requestBoost(ADA, LONG, GLIDING)).isFalse();

        assertThat(tracker.ticksRemaining(ADA))
                .describedAs("the refused request did not restart the burn, nor lengthen it to LONG's 7")
                .isEqualTo(3);
    }

    /**
     * The relationship {@code BoostConfig} enforces, observed rather than assumed: the cooldown is
     * measured from the burn's <em>start</em>, so a racer is still cooling down for a while after the
     * burn has ended. Measured from the end instead, the wait here would be 9 ticks longer and the
     * first assertion below would read zero.
     */
    @Test
    void theCooldownIsMeasuredFromTheBurnsStartSoItOutlastsTheBurn() {
        tracker.requestBoost(ADA, SHORT, GLIDING);
        assertThat(tracker.cooldownTicksRemaining(ADA)).isEqualTo(9);

        for (int tick = 0; tick < 4; tick++) {
            tracker.advance();
        }

        assertThat(tracker.burning(ADA)).describedAs("the 4-tick burn is over").isFalse();
        assertThat(tracker.cooldownTicksRemaining(ADA))
                .describedAs("9 measured from the start leaves 5 after a burn of 4")
                .isEqualTo(5);
        assertThat(tracker.requestBoost(ADA, SHORT, GLIDING)).isFalse();
    }

    @Test
    void aBoostIsAllowedAgainOnTheTickTheCooldownRunsOutAndNotBefore() {
        tracker.requestBoost(ADA, SHORT, GLIDING);
        for (int tick = 0; tick < 8; tick++) {
            tracker.advance();
        }

        assertThat(tracker.cooldownTicksRemaining(ADA)).isEqualTo(1);
        assertThat(tracker.requestBoost(ADA, SHORT, GLIDING))
                .describedAs("one tick of cooldown left is still a refusal")
                .isFalse();

        tracker.advance();

        assertThat(tracker.cooldownTicksRemaining(ADA)).isZero();
        assertThat(tracker.requestBoost(ADA, SHORT, GLIDING)).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // Who may boost
    // ------------------------------------------------------------------------------------------

    /**
     * Vanilla's rocket boosts an entity that is fall-flying and nobody else, so a rocket used on the
     * ground moves nothing. A server that started a burn for it would report a boost the client never
     * felt and then hold the racer in a cooldown for a boost they never got.
     */
    @Test
    void aBoostAskedForWhileNotGlidingIsRefusedAndCostsNoCooldown() {
        assertThat(tracker.requestBoost(ADA, SHORT, ON_FOOT)).isFalse();

        assertThat(tracker.burning(ADA)).isFalse();
        assertThat(tracker.cooldownTicksRemaining(ADA))
                .describedAs("a refused boost must not spend the cooldown of one that happened")
                .isZero();
        assertThat(tracker.requestBoost(ADA, SHORT, GLIDING)).isTrue();
    }

    /**
     * Every counter belongs to one player. A tracker that held a single global burn would pass every
     * other test in this file, because each of them touches one racer at a time.
     */
    @Test
    void oneRacersBurnIsNotAnothers() {
        tracker.requestBoost(ADA, LONG, GLIDING);

        assertThat(tracker.burning(ADA)).isTrue();
        assertThat(tracker.burning(BEN)).isFalse();
        assertThat(tracker.ticksRemaining(BEN)).isZero();
        assertThat(tracker.requestBoost(BEN, SHORT, GLIDING))
                .describedAs("Ada's cooldown is not Ben's")
                .isTrue();
        assertThat(tracker.ticksRemaining(ADA)).isEqualTo(7);
        assertThat(tracker.ticksRemaining(BEN)).isEqualTo(4);
    }

    // ------------------------------------------------------------------------------------------
    // Forgetting
    // ------------------------------------------------------------------------------------------

    @Test
    void aRacerWhoDisconnectsMidBurnIsForgottenAndTakesNobodyElseWithThem() {
        tracker.requestBoost(ADA, SHORT, GLIDING);
        tracker.requestBoost(BEN, LONG, GLIDING);
        tracker.advance();

        tracker.forget(ADA);

        assertThat(tracker.burning(ADA)).isFalse();
        assertThat(tracker.ticksRemaining(ADA)).isZero();
        assertThat(tracker.cooldownTicksRemaining(ADA))
                .describedAs("the cooldown goes with the burn, so a reconnect is not held by one")
                .isZero();
        assertThat(tracker.ticksRemaining(BEN))
                .describedAs("the other racer's burn is untouched")
                .isEqualTo(6);
    }

    @Test
    void clearingDropsEveryRacersBurnAndCooldown() {
        tracker.requestBoost(ADA, SHORT, GLIDING);
        tracker.requestBoost(BEN, LONG, GLIDING);

        tracker.clear();

        assertThat(tracker.burning(ADA)).isFalse();
        assertThat(tracker.burning(BEN)).isFalse();
        assertThat(tracker.requestBoost(ADA, SHORT, GLIDING))
                .describedAs("a cup that restarted owes nobody the last one's cooldown")
                .isTrue();
        assertThat(tracker.requestBoost(BEN, LONG, GLIDING)).isTrue();
    }

    @Test
    void aRacerWhoNeverBoostedIsReadyAndCountsNothing() {
        tracker.advance();
        tracker.advance();

        assertThat(tracker.burning(ADA)).isFalse();
        assertThat(tracker.ticksRemaining(ADA)).isZero();
        assertThat(tracker.cooldownTicksRemaining(ADA)).isZero();
        assertThat(tracker.requestBoost(ADA, SHORT, GLIDING)).isTrue();
    }

    @Test
    void cancellingABurnEndsItAndKeepsTheCooldownOfTheBoostThatLitIt() {
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        boosts.requestBoost(ADA, new BoostConfig(4, 9), true);

        boosts.cancelBurn(ADA);

        assertThat(boosts.burning(ADA)).isFalse();
        assertThat(boosts.cooldownTicksRemaining(ADA))
                .describedAs("the cooldown is measured from the burn's start and a reset does not refresh it")
                .isEqualTo(9);
    }
}
