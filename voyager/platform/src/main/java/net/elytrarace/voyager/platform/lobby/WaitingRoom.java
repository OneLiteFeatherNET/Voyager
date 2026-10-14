package net.elytrarace.voyager.platform.lobby;

import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.handoff.HubHandoff;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.flow.StartGate;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * <p>With a lobby world configured ({@link LobbyTransition}) a racer who joins waits there. Without one, the first map's
 * spawn is where they stand, as before ADR-0029. The boss bar is shown there too, until the start is committed; the
 * server says at boot that no lobby is set.
 *
 * <h2>Two rules the gate does not know about</h2>
 *
 * <p>An operator's {@code /race start} starts the cup with nobody online. The gate would abort it on its first tick,
 * which is not what {@code /race start} promises, so the room holds an abort until a racer has been online since the
 * cup started.
 *
 * <p>A cup that finishes is not an abort, and the room does not move its racers to the lobby. Its results stay up for
 * {@link #RESULTS_LINGER_TICKS} ticks, and then every racer online is handed to the network hub (ADR-0033). The racers
 * the room has sent away are not counted again, so a finished cup starts no countdown for them; a racer who joins
 * afterwards starts the room as the minimum rule says.
 */
@NotNullByDefault
public final class WaitingRoom {

    /**
     * How many ticks the results of a finished cup stay up before every racer online is handed to the hub: 200 ticks,
     * which is 10 s at 20 TPS (ADR-0033). A constant, not a setting, and counted in ticks, so it needs no clock.
     */
    public static final int RESULTS_LINGER_TICKS = 200;

    /** The linger counter when no result is waiting to be handed off. */
    private static final int NO_RESULT = -1;

    private final CupSession session;
    private final CatalogHolder catalog;
    private final Supplier<Collection<Player>> players;
    private final int minimum;
    private final int maximum;
    private final @Nullable LobbyTransition lobby;
    private final HubHandoff hub;
    private final LobbyHud hud;
    private final LobbySidebar sidebar;
    private final @Nullable LobbyHologram hologram;
    private final Supplier<Instance> firstMapInstance;
    /** The racers the room has handed to the hub. They stay connected until the move, so the room does not count them. */
    private final Set<UUID> handedOff = new HashSet<>();
    /** Joins that arrive close together, told as one line; the window is counted in {@link #gameTicks}. */
    private final JoinBurst joins = new JoinBurst();
    /** The room's own tick count, which the join window is measured in. Starts at zero and counts each {@link #tick}. */
    private int gameTicks;
    private boolean racersSinceStart;
    private boolean running;
    /** Ticks since the cup's results were announced, or {@link #NO_RESULT}. */
    private int lingerTicks = NO_RESULT;

    /**
     * @param session the cup the room starts, cancels and aborts
     * @param catalog the catalogue holder; the lobby names the cup it currently holds (a pending reload shows only once a round
     *     promotes it, ADR-0033)
     * @param players everyone online, read on every tick
     * @param minimum how many racers a cup needs to start; at least one
     * @param maximum how many racers make a full server; at least one. It sets the join line and the full-lobby
     *     countdown, and it never refuses a racer
     * @param lobby   the lobby world racers who join wait in, or {@code null} when none is configured
     * @param hub     moves the racers of a finished cup to the network hub after the results linger
     * @param hud     the boss bar, one per racer
     * @param sidebar the sidebar that names the cup, one per racer
     * @param hologram the cup as a text display in the lobby, or {@code null} when the server has none
     * @param firstMapInstance the instance of the first map of the current catalogue. With no lobby the racers wait in
     *     it, so the bar is shown there; it is read only in that case
     * @throws IllegalArgumentException if {@code minimum} or {@code maximum} is below one
     */
    public WaitingRoom(CupSession session, CatalogHolder catalog, Supplier<Collection<Player>> players, int minimum,
            int maximum, @Nullable LobbyTransition lobby, HubHandoff hub, LobbyHud hud, LobbySidebar sidebar,
            @Nullable LobbyHologram hologram, Supplier<Instance> firstMapInstance) {
        if (minimum < 1) {
            throw new IllegalArgumentException("the minimum racer count must be at least 1, was %s".formatted(minimum));
        }
        if (maximum < 1) {
            throw new IllegalArgumentException("the maximum racer count must be at least 1, was %s".formatted(maximum));
        }
        this.session = session;
        this.catalog = catalog;
        this.players = players;
        this.minimum = minimum;
        this.maximum = maximum;
        this.lobby = lobby;
        this.hub = hub;
        this.hud = hud;
        this.sidebar = sidebar;
        this.hologram = hologram;
        this.firstMapInstance = firstMapInstance;
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
                racers().size(), minimum, switch (session.situation(StartGate.COMMIT_WINDOW)) {
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
        Collection<Player> online = racers();
        JoinBurst.Outcome outcome = joins.join(gameTicks, racer.getUsername());
        announceBurst(outcome.flushed(), online);
        if (outcome.sendNow()) {
            online.forEach(other -> other.sendMessage(Messages.playerJoined(racer.getUsername(), online.size(), maximum)));
        }
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
        sidebar.forget(playerId);
        handedOff.remove(playerId);
    }

    /**
     * One room tick, after the cup's own tick has run.
     *
     * <p>A cup that has just finished starts the linger on this tick. A cup that starts again during the linger drops it,
     * because the results it would have handed off are no longer the ones on screen. The hand-off is decided before the
     * count, so a racer who joined during the linger is counted only if they were not handed away.
     */
    public void tick() {
        gameTicks++;
        announceBurst(joins.flush(gameTicks), racers());
        if (running && !session.running() && session.cupFinished()) {
            lingerTicks = 0;
        } else if (lingerTicks != NO_RESULT) {
            advanceLinger();
        }

        Collection<Player> online = racers();
        int count = online.size();
        if (count > 0) {
            racersSinceStart = true;
        }

        StartGate.Situation situation = session.situation(StartGate.COMMIT_WINDOW);
        // While the results linger no countdown starts: the racers on the server are about to be sent to the hub, so a
        // start now would race the hand-off and cancel the linger.
        StartGate.Verdict verdict = lingerTicks != NO_RESULT
                ? StartGate.Verdict.HOLD
                : StartGate.decide(situation, count, minimum);
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

    /**
     * One tick of the linger. The results stay up until the counter reaches {@link #RESULTS_LINGER_TICKS}, and then every
     * racer online is handed to the hub. A cup that starts again during the linger ends it without a hand-off.
     */
    private void advanceLinger() {
        if (session.running()) {
            lingerTicks = NO_RESULT;
            return;
        }
        lingerTicks++;
        if (lingerTicks == RESULTS_LINGER_TICKS) {
            lingerTicks = NO_RESULT;
            handOff(racers());
        }
    }

    /**
     * Sends the racers to the hub and stops counting them. The hub decides where they land, so the room keeps no record of
     * the target.
     *
     * <p>TODO(com-bots): bots are excluded here, in the list the room sends. No bot exists yet (see {@link HubHandoff}).
     */
    private void handOff(List<Player> racers) {
        if (racers.isEmpty()) {
            return;
        }
        for (Player racer : racers) {
            Racers.hold(racer);
        }
        hub.send(racers);
        racers.forEach(racer -> handedOff.add(racer.getUuid()));
    }

    /**
     * The racers the room counts: everyone online, except the racers it has already handed to the hub. A handed-off racer
     * stays connected until the move, so the count has to skip them.
     */
    /**
     * Tells every racer online about a burst of joins: one held join is named as the single join it was, and two or
     * more are one line with the count. Nothing is sent for an empty burst.
     */
    private void announceBurst(List<String> names, Collection<Player> online) {
        if (names.isEmpty()) {
            return;
        }
        Component line = names.size() == 1
                ? Messages.playerJoined(names.getFirst(), online.size(), maximum)
                : Messages.playersJoined(names.size(), online.size(), maximum);
        online.forEach(other -> other.sendMessage(line));
    }

    private List<Player> racers() {
        return players.get().stream()
                .filter(racer -> !handedOff.contains(racer.getUuid()))
                .toList();
    }

    /** A full server shortens a countdown that has more than the full-lobby length left; see {@link StartGate}. */
    private void shortenIfFull(StartGate.Situation situation, int count) {
        StartGate.shortenedCountdown(situation, session.lobbyRemaining(), count, maximum)
                .ifPresent(session::shortenLobby);
    }

    /**
     * What each racer sees this tick on the boss bar: the waiting count, or the countdown, while they stand where the
     * racers wait. That is the lobby world when one is configured. With no lobby they wait in the first map, and the
     * bar is shown there until the start is committed: from then on the race's own bar is the only one, as it is with a
     * lobby once the racer has entered the first map. A racing cup shows no waiting bar.
     */
    private void announce(StartGate.Situation situation, Collection<Player> online, int count) {
        // Every lobby display reads this one board, so the sidebar, the hologram and the boss bar name the same cup.
        LobbyBoard board = LobbyBoard.of(catalog.current().cup());
        Component title = waitingTitle(situation, count, board);
        Instance waitingPlace = lobby != null ? lobby.instance() : firstMapInstance.get();
        float progress = situation == StartGate.Situation.WAITING ? 1.0f : countdownFraction();
        boolean waiting = situation != StartGate.Situation.RUNNING;
        for (Player racer : online) {
            sidebar.update(racer, waitingPlace, board, waiting);
            if (title == null) {
                hud.hide(racer);
            } else {
                hud.update(racer, waitingPlace, title, progress);
            }
        }
        if (hologram != null) {
            hologram.update(board);
        }
    }

    /** The line the bar reads in this situation, or {@code null} when no waiting bar is shown. */
    private @Nullable Component waitingTitle(StartGate.Situation situation, int count, LobbyBoard board) {
        String cup = board.cupName();
        return switch (situation) {
            case WAITING -> Messages.lobbyBossBarWaiting(cup, count, minimum);
            case COUNTDOWN -> Messages.lobbyBossBarCountdown(cup, session.lobbyRemaining());
            case COMMITTED -> lobby != null ? Messages.lobbyBossBarCountdown(cup, session.lobbyRemaining()) : null;
            case RUNNING -> null;
        };
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
