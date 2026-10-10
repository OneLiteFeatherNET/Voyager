package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.flow.StartGate;
import net.minestom.server.entity.Player;

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The room racers wait in until a cup starts, and the Minestom side of {@link StartGate}.
 *
 * <p>Each tick the room asks the cup for its situation, asks the gate what to do with the number of racers online,
 * and acts on the answer: start the cup, cancel the countdown, abort the cup, or hold. It then tells every racer
 * where things stand: how many are online and needed while waiting, how long the countdown has left, nothing while
 * racing. It runs after the cup's own tick, in the same guarded task, so one broken tick stops the cup once.
 *
 * <h2>Two rules the gate does not know about</h2>
 *
 * <p>An operator's {@code /race start} starts the cup with nobody online. The gate would abort it on its first tick,
 * which is not what {@code /race start} promises, so the room holds an abort until a racer has been online since the
 * cup started.
 *
 * <p>A cup that finishes is not an abort. Its racers are handed back their waiting loadout and placed at the first
 * map's spawn, and only then does the gate look at the room again, so a finished cup with enough racers starts the
 * next countdown on the same tick.
 */
@NotNullByDefault
public final class WaitingRoom {

    /** Places a racer at the first map's spawn, where a waiting racer stands. */
    @FunctionalInterface
    public interface WaitingSpawn {

        /**
         * @param racer the racer to place
         */
        void place(Player racer);
    }

    private final CupSession session;
    private final Supplier<Collection<Player>> players;
    private final int minimum;
    private final WaitingSpawn spawn;
    private boolean racersSinceStart;
    private boolean running;

    /**
     * @param session the cup the room starts, cancels and aborts
     * @param players everyone online, read on every tick
     * @param minimum how many racers a cup needs to start; at least one
     * @param spawn where a racer is placed when a finished cup returns the room to waiting
     * @throws IllegalArgumentException if {@code minimum} is below one
     */
    public WaitingRoom(CupSession session, Supplier<Collection<Player>> players, int minimum, WaitingSpawn spawn) {
        if (minimum < 1) {
            throw new IllegalArgumentException("the minimum racer count must be at least 1, was %s".formatted(minimum));
        }
        this.session = session;
        this.players = players;
        this.minimum = minimum;
        this.spawn = spawn;
    }

    /** How many racers a cup needs to start. */
    public int minimum() {
        return minimum;
    }

    /**
     * One line for {@code /race}: how many racers are online, the minimum, and where the room stands.
     *
     * @return the line, without a trailing newline
     */
    public String describe() {
        return "room: %s of %s racer(s) online, %s".formatted(
                players.get().size(), minimum, switch (session.situation(StartGate.COMMIT_WINDOW)) {
                    case WAITING -> "waiting";
                    case COUNTDOWN -> "counting down";
                    case COMMITTED -> "committed to the start";
                    case RUNNING -> "racing";
                });
    }

    /**
     * A racer has joined and been prepared. A racer who joins a cup that is racing is told so, and waits for the next
     * map. A racer who joins while the countdown runs is part of the start and is told nothing.
     *
     * @param racer the racer who joined
     */
    public void joined(Player racer) {
        if (session.situation(StartGate.COMMIT_WINDOW) == StartGate.Situation.RUNNING) {
            racer.sendMessage(Messages.joinedMidCup());
        }
    }

    /**
     * One room tick, after the cup's own tick has run.
     */
    public void tick() {
        Collection<Player> online = players.get();
        int count = online.size();
        if (count > 0) {
            racersSinceStart = true;
        }
        if (running && !session.running() && session.cupFinished()) {
            for (Player racer : online) {
                Racers.hold(racer);
                spawn.place(racer);
            }
        }

        StartGate.Situation situation = session.situation(StartGate.COMMIT_WINDOW);
        StartGate.Verdict verdict = StartGate.decide(situation, count, minimum);
        if (verdict == StartGate.Verdict.ABORT_CUP && !racersSinceStart) {
            verdict = StartGate.Verdict.HOLD;
        }
        switch (verdict) {
            case START_CUP -> {
                session.start(false);
                racersSinceStart = count > 0;
            }
            case CANCEL_COUNTDOWN -> {
                session.disarm();
                online.forEach(racer -> racer.sendMessage(Messages.lobbyCancelled(count, minimum)));
            }
            case ABORT_CUP -> {
                session.abort();
                racersSinceStart = false;
            }
            case HOLD -> {
                // Nothing to do: the situation and the head count are both acceptable.
            }
        }
        running = session.running();
        announce(session.situation(StartGate.COMMIT_WINDOW), online, count);
    }

    /** What each racer sees this tick: the waiting count, or the countdown, or nothing while racing. */
    private void announce(StartGate.Situation situation, Collection<Player> online, int count) {
        Consumer<Player> show = switch (situation) {
            case WAITING -> racer -> racer.sendActionBar(Messages.lobbyWaiting(count, minimum));
            case COUNTDOWN, COMMITTED -> racer -> racer.sendActionBar(Messages.lobbyCountdown(session.lobbyRemaining()));
            case RUNNING -> racer -> {
                // A racing cup shows its own HUD; the room says nothing.
            };
        };
        online.forEach(show);
    }
}
