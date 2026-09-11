package net.elytrarace.voyager.physics.trace;

import net.elytrarace.voyager.api.physics.FlightInput;
import net.elytrarace.voyager.physics.trace.exception.InvalidTraceFixtureException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * A {@link TraceFixture} read as a sequence of simulator inputs, together with the two places its
 * recording stops being a statement about {@code travelFallFlying}.
 *
 * <p>Neither of those is a tolerance and neither is a guess about the physics: both follow from what
 * E2a's recorder does, quoted below and cross-checked against the fixtures. Keeping them here rather
 * than inside {@link TraceReplay} keeps {@code TraceReplay}'s reading of {@link
 * TraceFixture.Tick#fireworkBoostActive} literal for the synthetic fixtures Task 6 built it against,
 * while the real recordings are interpreted exactly once, in one place, with the reason written
 * down.
 *
 * @param fixture the recording this was derived from
 * @param inputs one {@link FlightInput} per tick, at the tick's own index; index {@code 0} is the
 *     seed, which nothing produced, and holds {@code null}
 * @param lastComparableTick the last tick whose recorded state is still the output of Vanilla's
 *     fall-flying movement — see {@link #firstGroundedTick}
 * @param unaccountableBoostTicks the ticks whose recorded <em>velocity</em> the fixture cannot
 *     account for — see {@link #unaccountableBoostTicks(List)}
 */
record RecordedGlide(
        TraceFixture fixture,
        List<FlightInput> inputs,
        int lastComparableTick,
        NavigableSet<Integer> unaccountableBoostTicks) {

    static RecordedGlide of(TraceFixture fixture) {
        if (fixture == null) {
            throw new InvalidTraceFixtureException("fixture must not be null");
        }
        List<TraceFixture.Tick> ticks = fixture.ticks();
        double gravity = fixture.metadata().gravity();

        List<FlightInput> inputs = new ArrayList<>(ticks.size());
        inputs.add(null);
        for (int index = 1; index < ticks.size(); index++) {
            TraceFixture.Tick tick = ticks.get(index);
            inputs.add(new FlightInput(
                    tick.yaw(),
                    tick.pitch(),
                    boostRanDuring(ticks, index),
                    tick.fireworkTicksRemaining(),
                    gravity));
        }

        return new RecordedGlide(
                fixture,
                Collections.unmodifiableList(inputs),
                firstGroundedTick(ticks),
                Collections.unmodifiableNavigableSet(unaccountableBoostTicks(ticks)));
    }

    /** The recorded profile's name, for test display names and failure messages. */
    String profile() {
        return fixture.metadata().profile();
    }

    /**
     * The profile name, so a parameterised test's display name reads {@code steady-glide} rather
     * than a record dump of two hundred ticks.
     */
    @Override
    public String toString() {
        return profile();
    }

    /** The input for {@code tick}, which advances the recorded state at {@code tick - 1} onto it. */
    FlightInput inputFor(int tick) {
        return inputs.get(tick);
    }

    /**
     * Whether the recorded velocity at {@code tick} can be compared at all.
     *
     * <p>False only inside an {@linkplain #unaccountableBoostTicks(List) unaccountable boost
     * window}. The recorded <em>position</em> at such a tick is still comparable, and is still
     * compared: a tick's position follows from the velocity the glider carried <em>into</em> it,
     * which the previous tick's sample records in full however many impulses produced it. Only the
     * velocity leaving the tick depends on how many rockets fired during it.
     */
    boolean velocityIsComparableAt(int tick) {
        return !unaccountableBoostTicks.contains(tick);
    }

    /** How many of the comparable ticks carry a comparable velocity, for a coverage assertion. */
    int comparableVelocityTicks() {
        int comparable = 0;
        for (int tick = 1; tick <= lastComparableTick; tick++) {
            if (velocityIsComparableAt(tick)) {
                comparable++;
            }
        }
        return comparable;
    }

    /**
     * Whether a firework impulse ran during {@code tick}, which is <em>not</em> simply
     * {@code fireworkBoostActive} at that tick.
     *
     * <p>{@code GliderRunner.sampleGlider} reads its {@code activeFireworks} list after filtering
     * out every rocket that is {@code !isValid() || isDead()}, and the sample for tick {@code n} is
     * taken from a {@code runTaskLater(…, 1L)} callback — CraftBukkit's scheduler heartbeat runs at
     * the top of {@code tickChildren}, before the levels tick, so that sample is read at the start
     * of tick {@code n + 1}. A rocket that applied its impulse during tick {@code n} and detonated
     * at the end of that same tick is therefore already gone by the time the flag for tick {@code n}
     * is read, and the flag says {@code false} for a tick that was boosted.
     *
     * <p>{@code single-boost} shows exactly that: the flag is set on ticks {@code 30..51}, and the
     * recorded velocity at tick {@code 52} is still {@code 1.671612598} — above tick {@code 51}'s
     * {@code 1.671612475}, where an unboosted tick would have fallen to {@code 1.6497}. The flag is
     * exact at the leading edge and one tick short at the trailing one, so the impulse ran during
     * {@code tick} whenever the flag is set at {@code tick} or at {@code tick - 1}.
     */
    private static boolean boostRanDuring(List<TraceFixture.Tick> ticks, int tick) {
        return ticks.get(tick).fireworkBoostActive() || ticks.get(tick - 1).fireworkBoostActive();
    }

    /**
     * The first tick recorded with {@code onGround}, or the last tick if there is none.
     *
     * <p>Vanilla's {@code LivingEntity.updateFallFlying} ends the glide the moment the entity is on
     * the ground, so every tick after that one is the output of ordinary ground movement, not of
     * {@code travelFallFlying}. {@code landing} makes the difference unmistakable: from the tick
     * after touchdown its recorded {@code velY} is a constant {@code -0.078400002} — which is
     * {@code -0.08 * 0.98}, the non-flying gravity-and-drag step — and its {@code velZ} decays with
     * ground friction, roughly halving per tick instead of following the glide.
     *
     * <p>This module ports {@code travelFallFlying} and deliberately not {@code stopFallFlying} or
     * the branch it hands over to (see {@code ElytraSimulator}'s class javadoc), so those ticks lie
     * outside anything a threshold here could describe. They are not compared, and calling that a
     * tolerance would be wrong in both directions: no bound loose enough to pass them would still
     * catch a real defect, and the touchdown tick itself — the one this module does have to get
     * right, including the clamp to the block face, the zeroed {@code velY} and the {@code onGround}
     * flag — is compared like every other tick, and matches bit-for-bit.
     */
    private static int firstGroundedTick(List<TraceFixture.Tick> ticks) {
        for (TraceFixture.Tick tick : ticks) {
            if (tick.onGround()) {
                return tick.index();
            }
        }
        return ticks.size() - 1;
    }

    /**
     * Every tick belonging to a boost window whose impulse count the fixture cannot express.
     *
     * <p>{@link TraceFixture.Tick} carries a {@code boolean} and a single {@code
     * fireworkTicksRemaining}, which {@code GliderRunner.sampleGlider} computes as the
     * <em>maximum</em> over every attached rocket. One rocket and three rockets therefore record
     * identically, while Vanilla applies one impulse per rocket per tick — and the impulse blends
     * toward a terminal speed rather than adding, so a second rocket does not double the speed but
     * moves the burn's steady state closer to {@code 1.7 · lookAngle}. {@code chained-boosts}, whose
     * whole purpose is a second rocket igniting while the first still burns, records a steady
     * {@code velZ} of {@code 1.686154} where {@code single-boost} settles at {@code 1.671612}: the
     * fixture shows the effect and cannot say what produced it.
     *
     * <p>A second ignition is nonetheless visible in the record, because the field is a maximum over
     * rockets and a fresh rocket's remaining life exceeds the burning one's: inside a run of flagged
     * ticks the value otherwise falls by exactly one per tick, so any <em>increase</em> is a new
     * rocket. A run containing one is unaccountable in full — the count is unknown from the extra
     * ignition onward, and which rocket expires when cannot be read back from a maximum either, so
     * the run is not trusted before the ignition it announces.
     *
     * <p>This is a defect in the fixture format, not in the port and not in a threshold, and the fix
     * belongs in E2a's recorder: record the number of attached rockets per tick and bump {@code
     * formatVersion}. Until a re-recording exists these ticks are compared on position only. The
     * impulse's arithmetic is not left unpinned by that: {@code single-boost}'s entire burn is
     * accountable and is compared on velocity tick by tick, and {@code
     * ElytraSimulatorTest.theFireworkImpulsesXTermIsPinnedAtANonZeroYaw} pins the terms {@code
     * single-boost}'s yaw of {@code 0} collapses to zero.
     */
    private static NavigableSet<Integer> unaccountableBoostTicks(List<TraceFixture.Tick> ticks) {
        NavigableSet<Integer> unaccountable = new TreeSet<>();
        int runStart = -1;
        boolean reignited = false;
        for (int index = 0; index < ticks.size(); index++) {
            boolean flagged = ticks.get(index).fireworkBoostActive();
            if (flagged && runStart < 0) {
                runStart = index;
                reignited = false;
            } else if (flagged && remainingRose(ticks, index)) {
                reignited = true;
            }
            if (runStart >= 0 && (!flagged || index == ticks.size() - 1)) {
                if (reignited) {
                    // Through index inclusive, not index - 1: the tick that ends a run is boosted
                    // too (see boostRanDuring) and its impulse count is just as unknown.
                    for (int tick = runStart; tick <= index; tick++) {
                        unaccountable.add(tick);
                    }
                }
                runStart = -1;
            }
        }
        return unaccountable;
    }

    private static boolean remainingRose(List<TraceFixture.Tick> ticks, int index) {
        return ticks.get(index).fireworkTicksRemaining() > ticks.get(index - 1).fireworkTicksRemaining();
    }
}
