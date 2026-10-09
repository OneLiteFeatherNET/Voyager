package net.elytrarace.voyager.platform.tick;

import net.elytrarace.voyager.api.race.CupDefinition;
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
    private final RaceTimings timings;
    private final Duration step;
    private final BooleanSupplier everyPlayerFinished;
    private final RacePhaseListener listener;

    private RaceState state = RaceState.initial();
    private RaceClock clock;

    /**
     * @param step the wall-clock duration one {@link #onUpdate()} stands for. It has to match the
     *     interval this phase is actually ticked at — 50 ms from a 20 TPS server tick — or every
     *     phase length and every recorded race time is scaled by the difference.
     * @param everyPlayerFinished read once at the top of each tick, from the state the previous tick
     *     left behind; {@code true} ends the {@code GAME} phase early
     */
    public XerusPhaseDriver(CupDefinition cup, RaceTimings timings, Duration step,
            BooleanSupplier everyPlayerFinished, RacePhaseListener listener) {
        super("race-%s".formatted(cup.name()));
        this.cup = cup;
        this.timings = timings;
        this.step = step;
        this.everyPlayerFinished = everyPlayerFinished;
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
