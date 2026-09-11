package net.elytrarace.voyager.physics.trace;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.physics.CollisionSpace;
import net.elytrarace.voyager.api.physics.FlightState;
import net.elytrarace.voyager.physics.ElytraSimulator;
import net.elytrarace.voyager.physics.TickTrace;
import net.elytrarace.voyager.physics.step.ElytraStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The gate on stage E2: every fixture E2a recorded from a live Paper 26.2 server, replayed against
 * the port, at the bounds Task 7 measured.
 *
 * <p><b>The bounds are exactly zero</b> — not "within a tolerance". Position, velocity and the
 * {@code onGround} flag match the recording bit-for-bit on every comparable tick of every fixture,
 * and a free-running replay of a whole trace ends on the recorded position bit-for-bit as well. The
 * E2b plan carried {@code 1e-6} per tick and {@code 1e-2} cumulative as stated assumptions; those
 * are six and two orders too loose, and the per-profile measurements that replaced them are recorded
 * in {@code docs/reference/elytra-physics-26.2.md}. A bound with slack here would hide a real
 * defect: three were found by this suite, and two of them ({@code LivingEntity.aiStep}'s
 * {@code 0.003} deadzone, worth {@code 2–3e-03}, and the {@code float} entity half-width, worth
 * {@code 1.19e-08}) sit on either side of {@code 1e-6}.
 *
 * <p><b>Both forms of the comparison are run, and the one-step residual is the one that calibrates.</b>
 * A free-running replay measures the starting condition and the formula at once, and turns a single
 * slip into a long decaying tail — before the recorder's first-tick transient was fixed, a fixture
 * that is bit-exact step by step still accumulated {@code 8.2e-02} of free-running drift. So the
 * per-tick bound comes from {@link #everyRecordedPositionIsReproducedExactlyFromTheTickBefore},
 * which restarts from the recorded state every tick; the free-running replay is kept for what only
 * it can see — an error that compounds — under its own bound.
 *
 * <p><b>Two kinds of tick are not compared, both derived from the recording rather than chosen.</b>
 * {@link RecordedGlide} documents each: the ticks after a touchdown, where Vanilla has stopped
 * gliding and is running a formula this module deliberately does not port, and the ticks of a burn
 * in which a second rocket ignited, where the fixture format records one boolean and cannot say how
 * many impulses landed. Neither is a threshold, and the second is a defect in E2a's fixture format
 * with a named fix. The ticks they cover are still compared on position — only the velocity leaving
 * a multi-rocket tick is unknowable from the record.
 *
 * <p><b>Discovery, not a list.</b> Every {@code .json} under {@code /traces/} is replayed, so a
 * tenth profile is one committed file and no code change. {@link
 * #theRecordedProfilesAreAllOnTheClasspath} keeps that from silently becoming zero fixtures.
 */
class VanillaParityTest {

    /**
     * Vanilla's elytra tick is not bit-identical across CPU architectures, and neither is this port,
     * because both compute the lift term with {@link Math#cos(double)}. {@code Math.cos} is only
     * required to be within one ulp and may differ between implementations;
     * {@link StrictMath#cos(double)} is the reproducible one. On the amd64 JVM these fixtures were
     * recorded against, the two disagree on 5 of the 77 distinct pitches in {@code pitch-extremes},
     * by one ulp each — exactly enough to fail an assertion that demands zero.
     *
     * <p>A fixture is therefore only a valid oracle on an architecture whose {@code Math.cos}
     * matches the one that produced it. Switching the port to {@code StrictMath} would not fix
     * that: it would make the port disagree with Vanilla on the very machine the recording came
     * from. Measured, not assumed — swapping both trigonometric call sites turns this suite red on
     * amd64, which is how this guard came to exist.
     *
     * <p>So this is an architecture check, not a tolerance. Where the trigonometry differs, no
     * faithful port can reproduce the fixture, and the suite says so rather than quietly relaxing.
     * Re-record on that architecture to gate it there.
     */
    @BeforeAll
    static void requireTheArchitectureTheFixturesWereRecordedOn() {
        int divergences = 0;
        for (float pitch = -90.0f; pitch <= 90.0f; pitch += 0.5f) {
            float lean = pitch * (float) (Math.PI / 180.0);
            if (Double.doubleToRawLongBits(Math.cos(lean))
                    != Double.doubleToRawLongBits(StrictMath.cos(lean))) {
                divergences++;
            }
        }
        assumeTrue(divergences > 0,
                "this JVM's Math.cos agrees with StrictMath.cos on every sampled lean angle, so its "
                        + "trigonometry differs from the amd64 JVM these fixtures were recorded "
                        + "against, where the two disagree by one ulp on some angles. Vanilla itself "
                        + "would produce different numbers here, so the fixtures are not a valid "
                        + "oracle on this architecture — re-record to gate it.");
    }


    private static List<RecordedGlide> recordings() {
        return TraceFixtures.load();
    }

    /**
     * The fixtures whose every tick the format can account for — every recording except one whose
     * burn contains a second ignition. {@link
     * #theOnlyUnaccountableBoostWindowIsTheOneWithASecondIgnition} pins which those are.
     */
    private static List<RecordedGlide> accountableRecordings() {
        return recordings().stream().filter(glide -> glide.unaccountableBoostTicks().isEmpty()).toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordings")
    void everyRecordedPositionIsReproducedExactlyFromTheTickBefore(RecordedGlide glide) {
        CollisionSpace space = new RecordedCollisionSpace(glide.fixture().metadata().worldSlice());
        List<String> divergences = new ArrayList<>();

        for (int tick = 1; tick <= glide.lastComparableTick(); tick++) {
            TraceFixture.Tick before = glide.fixture().ticks().get(tick - 1);
            TraceFixture.Tick recorded = glide.fixture().ticks().get(tick);

            TickTrace trace = ElytraSimulator.tickTraced(stateOf(before), glide.inputFor(tick), space);
            FlightState simulated = trace.result();

            double error = simulated.position().minus(positionOf(recorded)).length();
            if (error != 0.0) {
                divergences.add(describe(tick, error, simulated, recorded, trace));
            }
            if (simulated.onGround() != recorded.onGround()) {
                divergences.add("tick %s: onGround simulated %s, recorded %s"
                        .formatted(tick, simulated.onGround(), recorded.onGround()));
            }
        }

        assertThat(divergences)
                .as("%s — one-step position residual over %s comparable ticks, bound 0.0 blocks",
                        glide.profile(), glide.lastComparableTick())
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordings")
    void everyAccountableRecordedVelocityIsReproducedExactlyFromTheTickBefore(RecordedGlide glide) {
        CollisionSpace space = new RecordedCollisionSpace(glide.fixture().metadata().worldSlice());
        List<String> divergences = new ArrayList<>();

        for (int tick = 1; tick <= glide.lastComparableTick(); tick++) {
            if (!glide.velocityIsComparableAt(tick)) {
                continue;
            }
            TraceFixture.Tick before = glide.fixture().ticks().get(tick - 1);
            TraceFixture.Tick recorded = glide.fixture().ticks().get(tick);

            TickTrace trace = ElytraSimulator.tickTraced(stateOf(before), glide.inputFor(tick), space);
            FlightState simulated = trace.result();

            double error = simulated.velocity().minus(velocityOf(recorded)).length();
            if (error != 0.0) {
                divergences.add(describe(tick, error, simulated, recorded, trace));
            }
        }

        assertThat(divergences)
                .as("%s — one-step velocity residual over %s accountable ticks, bound 0.0 blocks/tick",
                        glide.profile(), glide.comparableVelocityTicks())
                .isEmpty();
    }

    /**
     * The free-running form: seeded once from tick 0 and never corrected, so an error that compounds
     * shows up here and nowhere else. Its bound is separate from the per-tick one by construction —
     * see the class javadoc for why it must never be the only measurement.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("accountableRecordings")
    void aFreeRunningReplayEndsOnTheRecordedPositionExactly(RecordedGlide glide) {
        TraceReplay.ReplayReport report = TraceReplay.replay(
                glide, TraceReplay.DEFAULT_PER_TICK_THRESHOLD, TraceReplay.DEFAULT_DRIFT_THRESHOLD);

        assertThat(report.firstDivergingTick())
                .as("%s — first tick of a free-running replay to leave the %s-block per-tick bound "
                                + "(position error there %s, velocity error %s)",
                        glide.profile(), TraceReplay.DEFAULT_PER_TICK_THRESHOLD,
                        report.firstDivergingTickError(), report.firstDivergingTickVelocityError())
                .isEmpty();
        assertThat(report.finalDrift())
                .as("%s — free-running drift after %s ticks, bound %s blocks (summed per-tick error %s)",
                        glide.profile(), glide.lastComparableTick(), TraceReplay.DEFAULT_DRIFT_THRESHOLD,
                        report.summedTickError())
                .isZero();
    }

    /**
     * The nine profiles E2a recorded must all be replayed. Written as "contains", not "contains
     * exactly": a tenth fixture needs no code change, and deleting one of these does need a reason.
     */
    @Test
    void theRecordedProfilesAreAllOnTheClasspath() {
        assertThat(recordings().stream().map(RecordedGlide::profile))
                .contains("steady-glide", "climb-into-stall", "sustained-turn", "pitch-extremes",
                        "dive-and-pull-out", "single-boost", "chained-boosts", "wall-graze", "landing");
    }

    /**
     * The carve-out, stated as an assertion so it cannot quietly grow. Exactly one fixture has a
     * boost window the format cannot account for — {@code chained-boosts}, by design: its script
     * ignites a second rocket while the first still burns, which is the behaviour the format's single
     * {@code fireworkBoostActive} boolean and max-over-rockets {@code fireworkTicksRemaining} cannot
     * express. {@code single-boost}'s burn is accountable in full, detonation tick included, so the
     * impulse is compared on velocity for all 22 ticks of it.
     *
     * <p>This test fails if E2a re-records {@code chained-boosts} with a rocket count — which is the
     * fix, and which should then delete the carve-out rather than keep it.
     */
    @Test
    void theOnlyUnaccountableBoostWindowIsTheOneWithASecondIgnition() {
        Map<String, Integer> unaccountable = recordings().stream()
                .filter(glide -> !glide.unaccountableBoostTicks().isEmpty())
                .collect(Collectors.toMap(
                        RecordedGlide::profile, glide -> glide.unaccountableBoostTicks().size()));

        assertThat(unaccountable)
                .as("fixtures whose recorded velocity is compared on fewer than all their ticks")
                .containsOnlyKeys("chained-boosts");
        assertThat(unaccountable.get("chained-boosts"))
                .as("chained-boosts ticks 20..48 — the burn plus its detonation tick")
                .isEqualTo(29);

        RecordedGlide singleBoost = recordings().stream()
                .filter(glide -> glide.profile().equals("single-boost"))
                .findFirst()
                .orElseThrow();
        assertThat(singleBoost.comparableVelocityTicks())
                .as("single-boost — every tick's velocity is compared, burn included")
                .isEqualTo(singleBoost.lastComparableTick());
    }

    /**
     * {@code landing} is the only fixture that stops being a glide before its last tick, and the
     * tick it stops at is the touchdown — which is compared, not skipped. Written as an assertion so
     * that a fixture silently truncating to a handful of ticks (an {@code onGround} sample early in a
     * recording, say) shows up as a failure instead of as a suite that replays almost nothing.
     */
    @Test
    void onlyTheLandingFixtureStopsBeforeItsLastRecordedTick() {
        List<String> truncated = recordings().stream()
                .filter(glide -> glide.lastComparableTick() < glide.fixture().ticks().size() - 1)
                .map(glide -> "%s: %s of %s ticks".formatted(
                        glide.profile(), glide.lastComparableTick(), glide.fixture().ticks().size() - 1))
                .toList();

        assertThat(truncated).containsExactly("landing: 138 of 199 ticks");
        assertThat(recordings().stream()
                .filter(glide -> glide.lastComparableTick() < 100)
                .map(RecordedGlide::profile))
                .as("no fixture may collapse to a near-empty replay")
                .isEmpty();
    }

    private static FlightState stateOf(TraceFixture.Tick tick) {
        return new FlightState(positionOf(tick), velocityOf(tick), tick.yaw(), tick.pitch(), tick.onGround());
    }

    private static Vec3 positionOf(TraceFixture.Tick tick) {
        return new Vec3(tick.posX(), tick.posY(), tick.posZ());
    }

    private static Vec3 velocityOf(TraceFixture.Tick tick) {
        return new Vec3(tick.velX(), tick.velY(), tick.velZ());
    }

    /**
     * A divergence report that names the step, not only the tick. {@link TickTrace} carries the
     * velocity after each of Vanilla's five branches, which is what turns "tick 34 is wrong" into
     * "the velocity was already wrong at {@code DOWNWARD_GLIDE}" — the difference between a finding
     * and a guess. It is printed for a human to read; nothing here draws a verdict from it (see
     * {@link TraceReplay}'s javadoc for why an automatic one is not trustworthy).
     */
    private static String describe(
            int tick, double error, FlightState simulated, TraceFixture.Tick recorded, TickTrace trace) {
        StringBuilder steps = new StringBuilder();
        for (Map.Entry<ElytraStep, Vec3> entry : trace.velocityAfter().entrySet()) {
            steps.append("%n      after %-21s %s".formatted(entry.getKey(), entry.getValue()));
        }
        return ("tick %s: error %s%n      simulated pos %s vel %s onGround %s%n"
                + "      recorded  pos %s vel %s onGround %s%s")
                .formatted(tick, error,
                        simulated.position(), simulated.velocity(), simulated.onGround(),
                        positionOf(recorded), velocityOf(recorded), recorded.onGround(),
                        steps);
    }
}
