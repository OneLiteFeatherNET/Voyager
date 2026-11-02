package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.platform.tick.RacePhaseListener;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.flow.RaceState;
import net.elytrarace.voyager.race.flow.RaceStateMachine;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.theevilreaper.xerus.api.phase.TickedPhase;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Drives a whole cup through {@link RaceStateMachine}, one {@link #onUpdate()} per tick, as a Xerus
 * phase.
 *
 * <h2>Why {@code TickedPhase} and not {@code TimedPhase}</h2>
 *
 * <p>Xerus's {@code TickingPhase} and {@code TimedPhase} schedule themselves through
 * {@code MinecraftServer.getSchedulerManager()}, which needs a running server and would make a race
 * untestable outside one. {@code TickedPhase} is the one that cannot tick by itself and must be
 * updated from outside — and since this driver never registers an event node,
 * {@code GamePhase.start()} and {@code finish()} never reach for {@code MinecraftServer} either. So
 * a whole cup runs in a plain JUnit method, driven by a bare loop, and on a server the same
 * {@code onUpdate()} is called from the server tick. The timers live here rather than in
 * {@code voyager-race} precisely so that module never has to see Xerus.
 *
 * <h2>The order within a tick</h2>
 *
 * <p>The phase is advanced first, and the movement tick is then played against the state the advance
 * produced — the same order {@code CupPlaythroughTest} established in E3. A driver that played the
 * movement tick before advancing would slip one extra tick into the {@code LOBBY -> GAME} transition
 * and play {@code race/step + 1} of them.
 *
 * <p>{@link RaceClock#advanced()} is called before {@link RacePhaseListener#raceTick(RaceClock)},
 * so the clock names the tick being played. That is the race clock. {@code RaceState.inPhase()},
 * which this driver also exposes through {@link #state()}, is one step behind it on every movement
 * tick and must not be timed from; see {@link RaceClock}.
 *
 * <h2>{@code PRACTICE} cups</h2>
 *
 * <p>A {@code PRACTICE} cup never sets {@code cupFinished}, so this phase never finishes on its own.
 * Something outside has to call {@link #finish()} — the last player leaving, an operator stopping
 * it, a tick budget. That is {@link RaceStateMachine}'s deliberate design, not an oversight here.
 */
public final class XerusPhaseDriver extends TickedPhase {

    private final CupDefinition cup;
    /**
     * The lengths the cup is played with. Replaced, never mutated, and only by {@link #shortenLobby}: the lobby is the
     * one phase whose length a running cup may change, and it changes only downwards.
     */
    private RaceTimings timings;
    private final Duration step;
    private final BooleanSupplier everyPlayerFinished;
    private final BooleanSupplier racersLoading;
    private final RacePhaseListener listener;

    private RaceState state = RaceState.initial();
    private RaceClock clock;

    /** How much GO has been held in the current lobby, for {@link RaceTimings#GO_HOLD_LIMIT}. Zero outside a hold. */
    private Duration goHeld = Duration.ZERO;

    /** Whether the tick just played held GO. */
    private boolean holdingGo;

    /** Whether the GO that began the current {@code GAME} phase was held for racers still loading. */
    private boolean startedAfterHold;

    /**
     * @param step the wall-clock duration one {@link #onUpdate()} stands for. It has to match the
     *     interval this phase is actually ticked at — 50 ms from a 20 TPS server tick — or every
     *     phase length and every recorded race time is scaled by the difference.
     * @param everyPlayerFinished read once at the top of each tick, from the state the previous tick
     *     left behind; {@code true} ends the {@code GAME} phase early
     */
    public XerusPhaseDriver(CupDefinition cup, RaceTimings timings, Duration step,
            BooleanSupplier everyPlayerFinished, RacePhaseListener listener) {
        this(cup, timings, step, everyPlayerFinished, listener, () -> false);
    }

    /**
     * @param racersLoading read on each tick on which a lobby would end. {@code true} holds {@code GO} there, for at
     *     most {@link RaceTimings#GO_HOLD_LIMIT}, so the racers who are still loading are not left behind
     */
    public XerusPhaseDriver(CupDefinition cup, RaceTimings timings, Duration step,
            BooleanSupplier everyPlayerFinished, RacePhaseListener listener, BooleanSupplier racersLoading) {
        super("race-%s".formatted(cup.key().asString()));
        this.cup = cup;
        this.timings = timings;
        this.step = step;
        this.everyPlayerFinished = everyPlayerFinished;
        this.racersLoading = racersLoading;
        this.listener = listener;
        this.clock = RaceClock.startingAt(step);
    }

    /**
     * Deliberately empty. Every other Xerus phase schedules its own repeating task here; this one is
     * ticked from outside, which is what keeps a whole cup runnable without a server.
     */
    @Override
    protected void onStart() {
        // Nothing to schedule — see the class javadoc.
    }

    /** Advances the cup by one tick. Does nothing before {@link #start()} or after {@link #finish()}. */
    @Override
    public void onUpdate() {
        if (!isRunning()) {
            return;
        }
        RaceState previous = state;
        RaceState next = RaceStateMachine.advance(
                previous, cup, timings, step, everyPlayerFinished.getAsBoolean());
        holdingGo = false;
        if (previous.phase() == RacePhase.LOBBY && next.phase() == RacePhase.GAME) {
            if (holdGo()) {
                // The lobby stands on the tick it would have ended on, so GAME begins with the tick GO is released on.
                next = new RaceState(RacePhase.LOBBY, previous.mapIndex(), previous.inPhase(), false);
            } else {
                startedAfterHold = goHeld.isPositive();
                goHeld = Duration.ZERO;
            }
        }
        // Published before the listeners run, so a listener asking for state() during a movement
        // tick sees the state that tick is being played against and not the one it came from.
        state = next;

        if (next.phase() == RacePhase.LOBBY) {
            listener.lobbyTick(next.mapIndex(), mapNameAt(next.mapIndex()), remainingLobby(next));
        }

        boolean enteringGame = next.phase() == RacePhase.GAME && previous.phase() != RacePhase.GAME;
        if (enteringGame) {
            clock = RaceClock.startingAt(step);
            listener.mapStarted(next.mapIndex(), mapNameAt(next.mapIndex()));
        }
        if (next.phase() == RacePhase.GAME) {
            clock = clock.advanced();
            listener.raceTick(clock);
        }
        if (previous.phase() == RacePhase.GAME && next.phase() != RacePhase.GAME) {
            listener.mapFinished(previous.mapIndex(), mapNameAt(previous.mapIndex()), clock);
        }
        if (next.cupFinished()) {
            finish();
        }
    }

    /**
     * Where the cup currently stands. {@link RaceState#inPhase()} is <em>not</em> the race clock —
     * {@link #clock()} is.
     */
    public RaceState state() {
        return state;
    }

    /** The race clock of the current {@code GAME} phase; see {@link RaceClock}. */
    public RaceClock clock() {
        return clock;
    }

    /** Whether the tick just played held {@code GO} for racers still loading. */
    public boolean goHeld() {
        return holdingGo;
    }

    /**
     * Whether the {@code GO} that began the current {@code GAME} phase was held for racers still loading. Read on the tick
     * GO fires, and it stays true for that map's {@code GAME} phase.
     */
    public boolean goStartedAfterHold() {
        return startedAfterHold;
    }

    /**
     * Whether the tick on which a lobby would end holds {@code GO} instead, and if so, counts the hold.
     *
     * <p>A hold lasts while a racer is still loading and {@link RaceTimings#GO_HOLD_LIMIT} has not been used up. The
     * limit is counted in steps on this driver's own clock, so it is the same length at every step, and the racers are
     * read only on a tick that would start the {@code GAME} phase, never during the lobby.
     *
     * <p>A lobby of zero length is never held. It has no countdown, so its {@code GO} is the operator's skip, which fires
     * on the next tick as it always has.
     */
    private boolean holdGo() {
        if (timings.lobby().isZero() || goHeld.compareTo(RaceTimings.GO_HOLD_LIMIT) >= 0
                || !racersLoading.getAsBoolean()) {
            return false;
        }
        goHeld = goHeld.plus(step);
        holdingGo = true;
        return true;
    }

    /**
     * How much of the lobby is left after the tick {@link #state()} describes. The start gate asks this to tell a
     * countdown from a committed start.
     */
    public Duration remainingLobby() {
        return remainingLobby(state);
    }

    /** The length the first lobby is played with now, which is shorter than configured once it has been shortened. */
    public Duration lobbyLength() {
        return timings.lobby();
    }

    /**
     * Leaves {@code remaining} of the lobby from the tick {@link #state()} describes, and never more than it had.
     *
     * <p>The phase is ticked from outside and reads its length from {@link RaceTimings} on every tick, so a new length
     * takes effect on the next {@link #onUpdate()}. There is no Xerus timer to reschedule. A request for more time than
     * the lobby already has is ignored, so a shortened countdown is never lengthened.
     *
     * @param remaining how much lobby is to be left, counted from the current tick; positive
     */
    public void shortenLobby(Duration remaining) {
        Duration target = state.inPhase().plus(remaining);
        if (target.compareTo(timings.lobby()) >= 0) {
            return;
        }
        timings = new RaceTimings(target, timings.race(), timings.endBetweenMaps(), timings.endAfterLastMap());
    }

    /**
     * How much lobby is left after the tick {@code state} describes.
     *
     * <p>{@code inPhase} is the lobby time this tick has taken the phase <em>to</em>, because the
     * state was advanced before this is read — so the subtraction is the time still to come and not
     * the time still to come plus one tick. Clamped at zero for the same reason the state machine
     * carries an overshoot: a step that lands exactly on the boundary has already become
     * {@code GAME}, so a negative here would mean the phase was misread rather than that the lobby
     * ran long.
     */
    private Duration remainingLobby(RaceState state) {
        Duration remaining = timings.lobby().minus(state.inPhase());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private String mapNameAt(int mapIndex) {
        return cup.mapNames().get(mapIndex);
    }
}
