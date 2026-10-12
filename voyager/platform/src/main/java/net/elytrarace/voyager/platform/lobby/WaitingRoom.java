package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.flow.StartGate;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The room racers wait in until a cup starts, and the Minestom side of {@link StartGate}.
 *
 * <p>Each tick the room asks the cup for its situation, asks the gate what to do with the number of racers online,
 * and acts on the answer: start the cup, cancel the countdown, abort the cup, or hold. A full server shortens a
 * countdown that has time left on it. It then tells every racer where things stand: the join line is sent when a racer
 * joins, and the lobby boss bar carries the count or the countdown while the racer stands in the lobby. It runs after
 * the cup's own tick, in the same guarded task, so one broken tick stops the cup once.
 *
 * <h2>Where waiting racers stand</h2>
 *
 * <p>With a lobby world configured ({@link LobbyTransition}) a finished cup returns its racers there. Without one, the
 * first map's spawn is where they stand, as before ADR-0029, and the room has no lobby bar to show: the server says so
 * at boot.
 *
 * <h2>Two rules the gate does not know about</h2>
 *
 * <p>An operator's {@code /race start} starts the cup with nobody online. The gate would abort it on its first tick,
 * which is not what {@code /race start} promises, so the room holds an abort until a racer has been online since the
 * cup started.
 *
 * <p>A cup that finishes is not an abort. Its racers are handed back their waiting loadout and placed in the lobby (or,
 * with no lobby, at the first map's spawn), and only then does the gate look at the room again, so a finished cup with
 * enough racers starts the next countdown on the same tick.
 */
@NotNullByDefault
public final class WaitingRoom {

    /** Places a racer at the first map's spawn, where a waiting racer stands when no lobby world is configured. */
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
    private final int maximum;
    private final @Nullable LobbyTransition lobby;
    private final WaitingSpawn spawn;
    private final LobbyHud hud;
    private boolean racersSinceStart;
    private boolean running;

    /**
     * @param session the cup the room starts, cancels and aborts
     * @param players everyone online, read on every tick
     * @param minimum how many racers a cup needs to start; at least one
     * @param maximum how many racers make a full server; at least one. It sets the join line and the full-lobby
     *     countdown, and it never refuses a racer
     * @param lobby   the lobby world finished racers return to, or {@code null} when none is configured
     * @param spawn   where a racer is placed when a finished cup returns the room to waiting and there is no lobby
     * @param hud     the lobby boss bar, one per racer
     * @throws IllegalArgumentException if {@code minimum} or {@code maximum} is below one
     */
    public WaitingRoom(CupSession session, Supplier<Collection<Player>> players, int minimum, int maximum,
            @Nullable LobbyTransition lobby, WaitingSpawn spawn, LobbyHud hud) {
        if (minimum < 1) {
            throw new IllegalArgumentException("the minimum racer count must be at least 1, was %s".formatted(minimum));
        }
        if (maximum < 1) {
            throw new IllegalArgumentException("the maximum racer count must be at least 1, was %s".formatted(maximum));
        }
        this.session = session;
        this.players = players;
        this.minimum = minimum;
        this.maximum = maximum;
        this.lobby = lobby;
        this.spawn = spawn;
        this.hud = hud;
    }

    /** How many racers a cup needs to start. */
    public int minimum() {
        return minimum;
    }

    /** The lobby world waiting racers stand in, or empty when none is configured and they stand at the first map. */
    public Optional<LobbyTransition> lobby() {
        return Optional.ofNullable(lobby);
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
     * A racer has joined and been prepared. Every racer online is told who joined and how many are online against the
     * maximum; a racer who joins a cup that is racing is also told that the cup is running and that they join at the
     * next map. A racer who joins while the countdown runs is part of the start and is told nothing more.
     *
     * <p>A join is never refused, whatever the count: the maximum is soft (ADR-0029).
     *
     * @param racer the racer who joined
     */
    public void joined(Player racer) {
        Collection<Player> online = players.get();
        online.forEach(other -> other.sendMessage(Messages.playerJoined(racer.getUsername(), online.size(), maximum)));
        if (session.situation(StartGate.COMMIT_WINDOW) == StartGate.Situation.RUNNING) {
            racer.sendMessage(Messages.joinedMidCup());
        }
    }

    /**
     * A racer has left: their lobby bar is dropped with them.
     *
     * @param playerId the racer who disconnected
     */
    public void left(UUID playerId) {
        hud.forget(playerId);
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
            }
            if (lobby != null) {
                lobby.returnTo(online);
            } else {
                online.forEach(spawn::place);
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
        shortenIfFull(session.situation(StartGate.COMMIT_WINDOW), count);
        announce(session.situation(StartGate.COMMIT_WINDOW), online, count);
    }

    /** A full server shortens a countdown that has more than the full-lobby length left; see {@link StartGate}. */
    private void shortenIfFull(StartGate.Situation situation, int count) {
        StartGate.shortenedCountdown(situation, session.lobbyRemaining(), count, maximum)
                .ifPresent(session::shortenLobby);
    }

    /**
     * What each racer in the lobby sees this tick: the waiting count, or the countdown. A racer outside the lobby, or a
     * racing cup, sees no lobby bar.
     */
    private void announce(StartGate.Situation situation, Collection<Player> online, int count) {
        if (lobby == null) {
            return;
        }
        Component title = switch (situation) {
            case WAITING -> Messages.lobbyBossBarWaiting(count, minimum);
            case COUNTDOWN, COMMITTED -> Messages.lobbyBossBarCountdown(session.lobbyRemaining());
            case RUNNING -> null;
        };
        float progress = situation == StartGate.Situation.WAITING ? 1.0f : countdownFraction();
        for (Player racer : online) {
            if (title == null) {
                hud.hide(racer);
            } else {
                hud.update(racer, lobby.instance(), title, progress);
            }
        }
    }

    /** The fraction of the running lobby that is left, from zero to one. */
    private float countdownFraction() {
        Duration length = session.lobbyLength();
        if (length.isZero() || length.isNegative()) {
            return 0.0f;
        }
        double fraction = (double) session.lobbyRemaining().toMillis() / length.toMillis();
        return (float) Math.min(1.0, Math.max(0.0, fraction));
    }
}
