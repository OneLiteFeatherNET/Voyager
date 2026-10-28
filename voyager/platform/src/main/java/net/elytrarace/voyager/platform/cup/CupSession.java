package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.effect.RingEffect;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.catalog.writer.LayoutVersion;
import net.elytrarace.voyager.platform.convert.VelocityExit;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.flight.ArrivalFacing;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.hud.HudState;
import net.elytrarace.voyager.platform.hud.HudStates;
import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.elytrarace.voyager.platform.hud.RaceHud;
import net.elytrarace.voyager.platform.hud.StartCountdown;
import net.elytrarace.voyager.platform.render.GuideLineRenderer;
import net.elytrarace.voyager.platform.stats.CupCompletions;
import net.elytrarace.voyager.platform.stats.RaceCompletions;
import net.elytrarace.voyager.platform.stats.RaceParticipant;
import net.elytrarace.voyager.platform.stats.RacerTally;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.tick.FlightTick;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.tick.RacePhaseListener;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.PendingArrival;
import net.elytrarace.voyager.platform.world.WorldHealth;
import net.elytrarace.voyager.race.effect.RingEffectRegistry;
import net.elytrarace.voyager.race.effect.RingEffectSequence;
import net.elytrarace.voyager.race.glide.GlideOut;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.flow.RaceState;
import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.cup.CupStanding;
import net.elytrarace.voyager.race.cup.CupRound;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate;
import net.elytrarace.voyager.race.cup.MapFigures;
import net.elytrarace.voyager.race.reset.ResetCause;
import net.elytrarace.voyager.race.reset.ResetPlan;
import net.elytrarace.voyager.race.reset.RunReset;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.MapScorer;
import net.elytrarace.voyager.api.stats.CupCompletion;
import net.elytrarace.voyager.api.stats.RaceCompletion;
import net.elytrarace.voyager.api.stats.RaceRecorder;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.flight.Rockets;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.world.DimensionType;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One cup being played: the phase driver, the flight driver, the map transition and the scores, tied
 * together and advanced once per server tick.
 *
 * <h2>What one tick does, in order</h2>
 *
 * <ol>
 *   <li>{@code FlightTickDriver.tick()} — samples every online player and advances the server's own
 *       flight simulation. It runs first so that anything later in the tick asking what the server
 *       thinks a racer's velocity is gets this tick's answer rather than the previous one.</li>
 *   <li>{@code FireworkBoostTracker.advance()} — counts every burn and cooldown down by one. It runs
 *       <em>after</em> the flight driver has sampled, not before, so a burn configured for 30 ticks
 *       drives 30 of them: the sampler reads this tick's remaining count, and advancing first would
 *       spend a tick of it before anything observed it. See that class's javadoc.</li>
 *   <li>{@code XerusPhaseDriver.onUpdate()} — advances the cup, which calls back into this class's
 *       {@link #mapStarted}, {@link #raceTick} and {@link #mapFinished}.</li>
 * </ol>
 *
 * <h2>Which position a ring is crossed with</h2>
 *
 * <p>{@link #raceTick} advances each run with the player's <em>reported</em> position, not with the
 * position the flight simulation computed. Normal elytra flight is client-authoritative, matching
 * Vanilla: the client's position is where the player actually is and therefore where they actually
 * flew through a ring. The server's simulation is tracked alongside for velocity and boost maths —
 * that is what {@code FlightTickDriver} is for — and is not yet the authority on anything.
 *
 * <p>The two are printed side by side in {@link #describe()} so the drift is a reading rather than an
 * assumption. A burning firework no longer adds to it — {@code LivePlayerSampler} reports the burn
 * and the simulation applies Vanilla's impulse — but the server's burn window and the client's differ
 * by the round trip, which that class's javadoc names as the one thing the reading cannot be exact
 * about.
 *
 * <h2>What a racer is shown</h2>
 *
 * <p>{@link #raceTick} also draws each racer the stretch of racing line ahead of them, through
 * {@code GuideLineRenderer}. Per racer rather than per world, because two racers at different rings
 * need different stretches; after the run has advanced rather than before, because a racer who
 * passed a ring on this tick is heading for the next one from this tick.
 *
 * <h2>When the cup starts</h2>
 *
 * <p>Not at boot. A cup started with nobody online runs its whole rotation to an empty world: the
 * transition moves nobody, {@code RaceRuns.everyRacerFinished()} answers {@code false} on an empty
 * board and so cannot end a phase early, and every map plays out its full race length. With one map
 * in the committed cup that is the entire cup, gone before the first player finishes connecting. So
 * the cup is armed at boot and started by the first player to join, and {@code /race start} restarts
 * it on demand.
 *
 * <p>A player who joins while a cup is running is placed on the next map when their preload completes,
 * if a wait is running, and holds no run until GO. Before that, nothing moves them. That is the second
 * reason {@code /race start} exists.
 *
 * <h2>Where a racer waits, and how they get there</h2>
 *
 * <p>A racer is placed on the next map as soon as their own wait has started: when they finished the
 * previous map and their glide-out has ended ({@code finish-glide-out}), or when the previous map's
 * {@code GAME} phase ends. A racer placed on a map is held at its spawn, facing the first ring, until
 * GO; a racer who has glided out is held where the glide ended until placed. The chunks around a spawn
 * load in the background, so no tick waits for them ({@link PendingArrival}).
 */
public final class CupSession implements RacePhaseListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CupSession.class);

    /** {@link #preparedMapIndex} when no map of the current cup has been entered yet. */
    private static final int NO_MAP = -1;

    private final CatalogHolder catalog;
    private final MapInstances instances;
    private final MapTransition transition;
    private final RaceRuns runs;
    private final FlightTickDriver flight;
    private final CurrentMapBlocks blocks;
    private final FireworkBoostTracker boosts;
    private final RaceTimings timings;
    private final Duration step;
    private final Supplier<Collection<Player>> players;
    private final CupRound round = new CupRound();
    private final CupAnnouncer announcer;
    private final RunResetter resetter;
    private final CompetitorField competitors;

    /** The computer racers' finishes, kept for the results only: they are never in {@link #round}. */
    private final ComStandings computerRacers = new ComStandings();

    /** The computer racers seated on the map being raced, counted into its completion's bot count. Set by {@link #mapStarted}. */
    private List<UUID> seatedCompetitors = List.of();

    /** What each racer boosted, reset and glided on the map being raced. Cleared when a map is entered. */
    private final RacerTally tally;

    /** Where a finished map and a finished cup are handed for storage. A failure there is logged, never raised. */
    private final RaceRecorder recorder;

    /** The clock the start of each map and each cup is read from, so a test can fix it. */
    private final Clock clock;

    /** When the current map's {@code GAME} phase began. Set by {@link #mapStarted}. */
    private Instant mapStartedAt;

    /** When the current cup started. Set by {@link #start(boolean)}. */
    private Instant cupStartedAt;

    /**
     * The cup being played: the boot cup until a round starts, then the cup of the catalogue that round pinned.
     * Written only by {@link #start}.
     */
    private CupDefinition cup;

    /**
     * The catalogue the current round pinned in {@link #start}, or {@code null} before the first round. Every
     * map a round enters comes from here, so a reload applied during the round cannot change it.
     */
    private @Nullable LoadedCatalog pinned;

    /**
     * The racing line, drawn per racer. Built here rather than injected for the same reason the three
     * objects {@link #create} builds are: it is implementation detail of how a cup is played — it
     * holds one sampled line per map and nothing else, and nothing outside a running race has a use
     * for it.
     */
    private final GuideLineRenderer lines = new GuideLineRenderer();

    /** The flight HUD, for the same reason {@link #lines} is built here rather than injected. */
    private final RaceHud hud = new RaceHud();

    /** The last three seconds of the current lobby. Reset per map; see {@link #lobbyTick}. */
    private final StartCountdown countdown = new StartCountdown();

    /** The last simulated tick per player, kept only so {@link #describe()} can show the drift. */
    private final Map<UUID, FlightTick> lastSimulated = new HashMap<>();

    private @Nullable XerusPhaseDriver driver;
    private @Nullable MapDefinition currentMap;

    /**
     * The ring effects of the map being raced, built from its {@code ringEffects} when the map is entered. Until
     * then it holds the default factors, which no race reads: {@link #raceTick} does nothing without a current map.
     */
    private RingEffectRegistry ringEffects = RingEffectRegistry.create();

    /**
     * Which map of the rotation has already been moved to, or {@link #NO_MAP} for none.
     *
     * <p>A map is entered by the start countdown three seconds before its {@code GAME} phase begins,
     * and by {@link #mapStarted} if no countdown ran — a lobby of zero length has no ticks to count
     * in. This is what stops the second of those from happening twice.
     */
    private int preparedMapIndex = NO_MAP;

    private boolean skipRequested;

    /**
     * Whether a map of the current cup has begun its {@code GAME} phase. The first lobby of a cup is the start
     * countdown only while this is false; a practice retry lobby on the same map is not, because a race has run.
     */
    private boolean gameStarted;

    /**
     * Whether a map's race is running, from its GO until its phase ends. Set by {@link #mapStarted} and cleared by
     * {@link #mapFinished}.
     */
    private boolean racing;

    /** The map index each racer is placed on, for every racer placed on a map that is not yet being raced. */
    private final Map<UUID, Integer> placedOn = new HashMap<>();

    /** Placements under way, by racer: the map each one is for, and the placement itself. */
    private final Map<UUID, Arrival> arrivals = new HashMap<>();

    /** Where each racer is held in place: from the end of a glide-out until placed, and from placement until GO. */
    private final Map<UUID, Pos> holds = new HashMap<>();

    /** Glide-outs under way, by racer. */
    private final Map<UUID, Glide> glides = new HashMap<>();

    /** Racers whose glide-out has ended, and who wait to be placed on the next map. */
    private final Set<UUID> glidedOut = new HashSet<>();

    /** Racers whose placement failed during this wait; they are placed again at the next wait or at GO. */
    private final Set<UUID> failedPlacement = new HashSet<>();

    /** Each racer's last position and velocity on a race tick, from which a glide-out's starting speed is read. */
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private final Map<UUID, Vec3> lastVelocities = new HashMap<>();

    /**
     * Builds a session from the collaborators the composition root wires. The flight driver samples the players
     * through the boost tracker and reads blocks through the block source, so those three belong together; the
     * composition root builds them as one graph.
     *
     * @param catalog the holder whose current catalogue each {@link #start} pins
     * @param step the wall-clock duration one {@link #tick()} stands for; 50 ms on a 20 TPS server.
     *     It has to match the interval this is actually ticked at or every phase length and every
     *     recorded race time is scaled by the difference.
     * @param recorder where each finished map and each finished cup is handed; {@code NoOpRaceRecorder} when statistics
     *     are off
     * @param clock the clock the start of each map and cup is read from; the system clock in production
     */
    public CupSession(CatalogHolder catalog, MapInstances instances,
            MapTransition transition, RaceRuns runs, FlightTickDriver flight, CurrentMapBlocks blocks,
            FireworkBoostTracker boosts, RaceTimings timings, Duration step, Supplier<Collection<Player>> players,
            RaceRecorder recorder, Clock clock) {
        this(catalog, instances, transition, runs, flight, blocks, boosts, timings, step, players, recorder, clock,
                NoCompetitors.INSTANCE);
    }

    /**
     * Builds a session that also plays the computer racers {@code competitors} reports. The same collaborators as
     * {@link #CupSession(CatalogHolder, MapInstances, MapTransition, RaceRuns, FlightTickDriver, CurrentMapBlocks,
     * FireworkBoostTracker, RaceTimings, Duration, Supplier, RaceRecorder, Clock)}, and the competitor port last.
     *
     * @param competitors the computer racers; {@link NoCompetitors} for a cup with none
     */
    public CupSession(CatalogHolder catalog, MapInstances instances,
            MapTransition transition, RaceRuns runs, FlightTickDriver flight, CurrentMapBlocks blocks,
            FireworkBoostTracker boosts, RaceTimings timings, Duration step, Supplier<Collection<Player>> players,
            RaceRecorder recorder, Clock clock, CompetitorField competitors) {
        this.catalog = catalog;
        this.competitors = competitors;
        this.cup = catalog.current().cup();
        this.instances = instances;
        this.transition = transition;
        this.runs = runs;
        this.flight = flight;
        this.blocks = blocks;
        this.boosts = boosts;
        this.timings = timings;
        this.step = step;
        this.players = players;
        this.announcer = new CupAnnouncer(players);
        this.resetter = new RunResetter(runs, boosts, flight);
        this.tally = new RacerTally(step);
        this.recorder = recorder;
        this.clock = clock;
        this.mapStartedAt = clock.instant();
        this.cupStartedAt = clock.instant();
    }

    /** The cup being played: the one the current round pinned, or the boot cup before the first round. */
    public CupDefinition cup() {
        return cup;
    }

    /**
     * The catalogue the current round pinned, or the holder's current one before the first round.
     * Exists for the cup's tests, which live in the server module: they assert the pin, the game asks {@link #cup()}.
     */
    public LoadedCatalog pinned() {
        LoadedCatalog held = pinned;
        return held != null ? held : catalog.current();
    }

    /** Whether a cup is currently running. */
    public boolean running() {
        XerusPhaseDriver current = driver;
        return current != null && current.isRunning();
    }

    /** Whether the cup has played its whole rotation and reached its terminal state. */
    public boolean cupFinished() {
        XerusPhaseDriver current = driver;
        return current != null && current.state().cupFinished();
    }

    /**
     * Starts the cup from its first map, replacing whatever was running.
     *
     * @param skipLobby play with a zero-length lobby, so the first map's {@code GAME} phase begins on
     *     the next tick. This is the {@code /dev-start} pattern the tree being replaced established,
     *     kept for the reason it was written: a two-minute lobby between two attempts is how a
     *     debugging session turns into an afternoon.
     */
    public void start(boolean skipLobby) {
        XerusPhaseDriver previous = driver;
        if (previous != null && previous.isRunning()) {
            previous.finish();
        }
        // The one place a pending catalogue becomes current, and the round then plays only what it pinned here.
        pinned = catalog.promoteForNewRound();
        cup = pinned.cup();
        round.reset();
        tally.clear();
        cupStartedAt = clock.instant();
        computerRacers.clear();
        seatedCompetitors = List.of();
        lastSimulated.clear();
        // A restart owes nobody the abandoned cup's cooldown, and a burn lit on the map it abandons
        // must not still be running when the new first map releases its racers.
        boosts.clear();
        currentMap = null;
        preparedMapIndex = NO_MAP;
        gameStarted = false;
        countdown.reset();
        for (Player racer : players.get()) {
            hud.standDown(racer);
        }
        forgetPendingSkip();
        clearPlacements();
        RaceTimings played = skipLobby
                ? new RaceTimings(Duration.ZERO, timings.race(), timings.endBetweenMaps(),
                        timings.endAfterLastMap())
                : timings;
        XerusPhaseDriver next = new XerusPhaseDriver(cup, played, step, this::gamePhaseEndsNow, this);
        driver = next;
        next.start();
        LOGGER.info("Cup '{}' started: {} map(s), mode {}, lobby {}, race {}, results {} between maps "
                        + "and {} after the last", cup.key().asString(), cup.mapNames().size(), cup.mode(),
                played.lobby(), played.race(), played.endBetweenMaps(), played.endAfterLastMap());
    }

    /**
     * Ends the current map's {@code GAME} phase on the next tick, as if everybody had finished.
     *
     * <p><strong>Refused when nothing is racing, rather than banked.</strong> A flag set during a
     * lobby survives until the first tick of the map that follows and ends <em>that</em> map instead —
     * a landmine armed minutes earlier by somebody who saw no effect at the time and reasonably
     * concluded the command was broken. So the answer comes back immediately: a skip either applies
     * to the map being raced now or does not happen.
     *
     * @return whether a {@code GAME} phase was running and the skip was taken
     */
    public boolean requestSkip() {
        XerusPhaseDriver current = driver;
        if (current == null || current.state().phase() != RacePhase.GAME) {
            return false;
        }
        skipRequested = true;
        return true;
    }

    /**
     * Where the cup stands for the start gate, given the commit window the gate uses.
     *
     * <p>{@code WAITING} when no cup runs, including a cup that has finished. Otherwise {@code COUNTDOWN} or
     * {@code COMMITTED} while the cup is in its first lobby on its first map and no race has begun, split at
     * {@code commitWindow} of lobby left; and {@code RUNNING} at every other point.
     *
     * @param commitWindow how much of the first lobby is committed; {@link StartGate#COMMIT_WINDOW} in production
     */
    public StartGate.Situation situation(Duration commitWindow) {
        XerusPhaseDriver current = driver;
        if (current == null || !current.isRunning()) {
            return StartGate.Situation.WAITING;
        }
        boolean firstLobby = current.state().phase() == RacePhase.LOBBY
                && current.state().mapIndex() == 0
                && !gameStarted;
        if (!firstLobby) {
            return StartGate.Situation.RUNNING;
        }
        return current.remainingLobby().compareTo(commitWindow) > 0
                ? StartGate.Situation.COUNTDOWN
                : StartGate.Situation.COMMITTED;
    }

    /**
     * How much of the first lobby is left, or zero when no cup is running. The start countdown shows it.
     */
    public Duration lobbyRemaining() {
        XerusPhaseDriver current = driver;
        return current == null || !current.isRunning() ? Duration.ZERO : current.remainingLobby();
    }

    /** The length the running lobby is played with, which a full server may have shortened. Zero when no cup runs. */
    public Duration lobbyLength() {
        XerusPhaseDriver current = driver;
        return current == null || !current.isRunning() ? Duration.ZERO : current.lobbyLength();
    }

    /**
     * Shortens a countdown that is still counting down so that {@code remaining} is left from the next tick, for a full
     * server (see {@code StartGate.shortenedCountdown}).
     *
     * <p>Only the first lobby's countdown, and only before the start is committed. Anywhere else this does nothing: a
     * committed start is never shortened, and the driver never lengthens a lobby it has already shortened.
     *
     * @param remaining how much of the lobby is to be left
     */
    public void shortenLobby(Duration remaining) {
        XerusPhaseDriver current = driver;
        if (current == null || situation(StartGate.COMMIT_WINDOW) != StartGate.Situation.COUNTDOWN) {
            return;
        }
        current.shortenLobby(remaining);
    }

    /**
     * Cancels a countdown that has not yet entered a map: the cup is not started, and the room returns to waiting.
     *
     * <p>Refused in any other situation. Once the last three seconds have begun the first map is already
     * prepared, and a cancel would leave racers standing in a world with no cup behind it.
     *
     * @throws IllegalStateException if the cup is not in {@link StartGate.Situation#COUNTDOWN}
     */
    public void disarm() {
        StartGate.Situation now = situation(StartGate.COMMIT_WINDOW);
        if (now != StartGate.Situation.COUNTDOWN) {
            throw new IllegalStateException(
                    "only a countdown that has not entered a map can be disarmed, the cup is %s".formatted(now));
        }
        driver.finish();
        driver = null;
        forgetLobby();
        for (Player racer : players.get()) {
            hud.standDown(racer);
        }
    }

    /**
     * Stops a cup that has lost every racer, without a result, and returns the room to waiting.
     *
     * <p>Every run is forgotten, every racer is stood down and handed back their waiting loadout. Unlike
     * {@link #stop()} this is the normal end of a cup with nobody left to race it, so nothing is kept for
     * {@code /race} to inspect. The next {@link #start(boolean)} pins the catalogue again.
     *
     * @throws IllegalStateException if the cup is not committed or running
     */
    public void abort() {
        StartGate.Situation now = situation(StartGate.COMMIT_WINDOW);
        if (now != StartGate.Situation.COMMITTED && now != StartGate.Situation.RUNNING) {
            throw new IllegalStateException("only a committed or running cup can be aborted, the cup is %s".formatted(now));
        }
        XerusPhaseDriver current = driver;
        if (current != null && current.isRunning()) {
            current.finish();
        }
        driver = null;
        forgetLobby();
        for (Player racer : players.get()) {
            UUID id = racer.getUuid();
            runs.forget(id);
            flight.forget(id);
            boosts.forget(id);
            hud.standDown(racer);
            Racers.standDown(racer);
            Racers.hold(racer);
        }
        competitors.dismiss();
        round.reset();
        computerRacers.clear();
        seatedCompetitors = List.of();
    }

    /** Drops everything a started or armed cup holds for its current lobby, so that {@link #describe()} reads not started. */
    private void forgetLobby() {
        currentMap = null;
        preparedMapIndex = NO_MAP;
        gameStarted = false;
        countdown.reset();
        lastSimulated.clear();
        forgetPendingSkip();
        blocks.follow(null);
        clearPlacements();
    }

    /**
     * Stops the cup where it stands, without scoring or announcing anything.
     *
     * <p>This is the failure path, not the normal end of a cup: {@code VoyagerServer} calls it when a
     * tick throws, so that one broken tick produces one stack trace and a stopped race rather than a
     * stack trace twenty times a second for as long as anybody is watching. The racers keep whatever
     * they were holding, deliberately — {@code /race} can then still be asked what the state was when
     * it broke.
     */
    public void stop() {
        XerusPhaseDriver current = driver;
        if (current != null && current.isRunning()) {
            current.finish();
        }
        blocks.follow(null);
    }

    /**
     * The flight step of a tick: samples every online player and advances the server's own flight simulation, and
     * keeps what it produced for {@link #describe()}. The composition root runs it first in the tick order.
     */
    public void sampleFlight() {
        for (FlightTick simulated : flight.tick()) {
            lastSimulated.put(simulated.playerId(), simulated);
        }
    }

    /**
     * The phase step of a tick: advances the cup, and announces the cup's result on the tick the cup finishes. The
     * composition root runs it last in the tick order, after the boost step.
     */
    public void advancePhase() {
        XerusPhaseDriver current = driver;
        if (current == null) {
            return;
        }
        glideStep();
        current.onUpdate();
        placementStep();
        if (current.state().cupFinished() && currentMap != null) {
            // mapFinished has already run for the last map by the time cupFinished is set, so every
            // per-map score is in. currentMap is what says this has not been announced yet.
            recordCup();
            announcer.announceCupResult(cup, round.order(), computerRacers.order());
            currentMap = null;
            blocks.follow(null);
            competitors.dismiss();
            clearPlacements();
        }
    }

    /**
     * The last tick the server's own flight simulation produced for {@code playerId}, if any.
     *
     * <p>Exists for the cup's tests, which live in the server module: the simulation is silent by
     * design — nothing in a race reads it, it only shadows the client — so the only other trace of it
     * is the line {@link #describe()} renders, and asserting a velocity by parsing a sentence is
     * asserting the sentence. Not part of the session's contract.
     */
    public Optional<FlightTick> lastSimulated(UUID playerId) {
        return Optional.ofNullable(lastSimulated.get(playerId));
    }

    /**
     * The board this cup is accumulating into.
     *
     * <p>Exists for the cup's tests, which live in the server module: the scores are otherwise visible
     * only as the text {@link #describe()} renders and the chat lines a racer is sent, and asserting
     * a medal tier by reading a sentence is asserting the sentence. Not part of the session's
     * contract — do not widen it.
     */
    public CupRound standings() {
        return round;
    }

    /** Drops everything held for a player who disconnected. */
    public void forget(UUID playerId) {
        flight.forget(playerId);
        runs.forget(playerId);
        boosts.forget(playerId);
        hud.forget(playerId);
        lastSimulated.remove(playerId);
        forgetPlacement(playerId);
    }

    /**
     * A racer asked to boost. Answers whether a rocket was actually lit.
     *
     * <p>The rule lives in {@code FireworkBoostTracker} and the entity in {@code Rockets}; this is
     * the one place that knows both, plus the third thing neither of them can know — which map is
     * being raced, and therefore whose tuning applies. A boost asked for between maps is refused
     * here, before the tracker is consulted, because there is no configuration to start a burn under.
     *
     * @param racer who used a rocket
     * @return whether a burn started, so the caller can tell a refusal from a boost
     */
    public boolean requestBoost(Player racer) {
        MapDefinition map = currentMap;
        if (map == null) {
            return false;
        }
        BoostConfig config = map.boostConfig();
        if (!boosts.requestBoost(racer.getUuid(), config, racer.isFlyingWithElytra())) {
            return false;
        }
        if (stillRacing(racer.getUuid())) {
            tally.recordBoost(racer.getUuid());
        }
        Rockets.fire(racer, config);
        return true;
    }

    /** Whether the racer holds a run that has not finished: the only runs whose counts the record keeps. */
    private boolean stillRacing(UUID playerId) {
        return runs.of(playerId).map(run -> !run.finished()).orElse(false);
    }

    /**
     * The start countdown, run out of the tail of the lobby, and the first tick of the lobby, which starts the wait.
     *
     * <p>The map is prepared on the first tick of its lobby, so the cup knows what it is playing; the placement of the
     * racers follows from the wait ({@link #beginWaitingPlacements}), not from the digits. A racer is therefore on the
     * spawn, facing the first ring, long before GO. The digits show the course the racer is about to fly. The {@code GAME}
     * phase still begins with GO on its first tick, and the three seconds come out of the lobby, which is time nobody was
     * playing in anyway.
     */
    @Override
    public void lobbyTick(int mapIndex, String mapName, Duration remaining) {
        MapDefinition map = prepareMap(mapIndex);
        int digit = countdown.show(remaining);
        if (digit == StartCountdown.NONE) {
            return;
        }
        Component subtitle = Messages.countdownSubtitle(
                map.displayName(), map.rings().size(), map.referenceTime());
        HudState armed = HudStates.of(MapFigures.starting(map, mapIndex + 1, cup.mapNames().size()));
        for (Player racer : players.get()) {
            RaceFeedback.countdown(racer, digit, subtitle);
            hud.arm(racer, armed);
        }
    }

    @Override
    public void mapStarted(int mapIndex, String mapName) {
        MapDefinition map = prepareMap(mapIndex);
        gameStarted = true;
        mapStartedAt = clock.instant();
        racing = true;

        // GO releases the racers placed on this map: each one begins its run here, and is no longer held. A racer who is
        // not placed yet is placed now, and begins the run when the placement completes. GO does not move a racer who is
        // already placed.
        List<Player> racers = List.copyOf(players.get());
        for (Player racer : racers) {
            UUID id = racer.getUuid();
            if (isPlacedOn(id, mapIndex)) {
                holds.remove(id);
                runs.startFresh(id);
            } else if (!arrivals.containsKey(id)) {
                begin(racer, mapIndex);
            }
        }
        failedPlacement.clear();
        // A competitor's run is started here and not carried over from the previous map.
        List<UUID> prepared = competitors.prepare(mapIndex, map);
        seatedCompetitors = prepared;
        computerRacers.seat(prepared);
        for (UUID competitor : prepared) {
            runs.startFresh(competitor);
        }
        for (Player racer : racers) {
            RaceFeedback.go(racer);
        }

        WorldHealth health = instances.healthOf(map.world());
        LOGGER.info("Map {}/{} '{}' started on world '{}' with {} racer(s) — {}",
                mapIndex + 1, cup.mapNames().size(), map.key().asString(), map.world(), racers.size(), health.describe());
        if (!health.isSound()) {
            LOGGER.warn("World '{}' is not sound: {}", map.world(), health.describe());
        }
    }

    /**
     * Makes {@code mapIndex} the map the cup is on: its definition, its world and block source, its ring effects, its
     * tally and its banner. It is done once per map and never moves a racer.
     *
     * <p>It runs on the first tick of a map's lobby and at the end of the map before it, so the cup-wide part of a map's
     * entry happens at the start of its wait, not three seconds before GO. A racer placed on this map before it was
     * prepared stays placed, and a racer placed for any other map is no longer waiting for this one.
     */
    private MapDefinition prepareMap(int mapIndex) {
        MapDefinition held = currentMap;
        if (preparedMapIndex == mapIndex && held != null) {
            return held;
        }
        MapDefinition map = mapAt(mapIndex);
        currentMap = map;
        preparedMapIndex = mapIndex;
        countdown.reset();
        failedPlacement.clear();
        placedOn.values().removeIf(placed -> placed != mapIndex);
        tally.clear();
        ringEffects = RingEffectRegistry.create(map.ringEffects());
        LOGGER.info("Map {}/{} '{}' tuning: {}", mapIndex + 1, cup.mapNames().size(), map.key().asString(), tuningOf(map));
        Instance instance = instances.forWorld(map.world());
        blocks.follow(instance);
        announcer.broadcast(Messages.mapBanner(mapIndex + 1, cup.mapNames().size(), map.displayName(),
                map.rings().size(), map.referenceTime()));
        return map;
    }

    /** The definition of the map at {@code mapIndex} in the cup, from the catalogue the round pinned. */
    private MapDefinition mapAt(int mapIndex) {
        String name = cup.mapNames().get(mapIndex);
        return pinned().snapshot().mapByName(name).orElseThrow(() -> new IllegalStateException(
                ("cup '%s' plays a map named '%s' that the round's catalogue does not hold; "
                        + "CatalogReloader checks the played cup's maps and should have refused this cup")
                        .formatted(cup.key().asString(), name)));
    }

    @Override
    public void raceTick(RaceClock clock) {
        MapDefinition map = currentMap;
        if (map == null) {
            return;
        }
        for (Player racer : players.get()) {
            UUID id = racer.getUuid();
            Optional<RaceRun> held = runs.of(id);
            if (held.isEmpty()) {
                continue;
            }
            if (leftBehind(id)) {
                // Placed on the next map: no longer racing this one. The run stays, for the phase end to score.
                continue;
            }
            boolean wasFinished = held.get().finished();
            Vec3 position = Vectors.toDomain(racer.getPosition());
            Vec3 previous = lastPositions.put(id, position);
            lastVelocities.put(id, previous == null ? Vec3.ZERO : position.minus(previous));
            RaceRun advanced = runs.advance(id, map, clock, position, racer.isFlyingWithElytra(), racer.isOnGround());
            // The finishing tick and every tick after it are not flight time: flight time stops at the finish.
            if (advanced.gliding() && !advanced.finished()) {
                tally.recordGlidingTick(id);
            }
            announcer.report(racer, map, advanced, wasFinished, clock);
            if (!wasFinished && advanced.finished()) {
                // The finish: one burst at the last ring, and the glide-out that brakes the flight after it.
                RaceFeedback.finish(racer, map.rings().getLast().center());
                startGlide(id, map, preparedMapIndex);
            }
            if (advanced.justMissed() != null) {
                // Ring numbers count from one; the ring's own index is zero-based.
                hud.missedRing(racer, advanced.justMissed().index() + 1, clock.gameTick());
            }
            RaceRun shown = sendBackIfLostOrLanded(racer, map, held.get(), advanced, position);
            // sendBackIfLostOrLanded hands back the run it was given unless it reset the racer, so a reset
            // cancels the effect of the ring it was crossed on. A reset racer has no ring effect on that tick.
            if (shown == advanced) {
                applyRingEffect(racer, map, advanced);
            }
            hud.render(racer, HudStates.of(MapFigures.inFlight(shown, clock, map, preparedMapIndex + 1,
                    cup.mapNames().size())));
            // After the advance, so the line is for the ring the racer is heading for now. The stretch
            // starts at the last ring passed, so the segment being flown stays drawn until the next ring
            // is passed. A racer who was sent back on this tick is shown the run they were sent back with.
            lines.render(racer, map, shown.progress().passedCount(), clock.gameTick());
        }
        // Competitors are advanced after the human loop: they are not in the players supplier, and nothing
        // above reads their runs. A competitor the cup holds no run for is not on this course, and is skipped.
        for (Competitor competitor : competitors.advance(clock)) {
            Optional<RaceRun> before = runs.of(competitor.id());
            if (before.isEmpty()) {
                continue;
            }
            RaceRun advanced = runs.advance(competitor.id(), map, clock, competitor.position(), competitor.gliding(),
                    false);
            if (!before.get().finished() && advanced.finished()) {
                Duration time = advanced.timeOnCourse(timings.race());
                ComStandings.Standing standing = computerRacers.finished(competitor.id(), time);
                if (standing != null) {
                    announcer.announceComputerFinish(standing.number(), time);
                }
            }
        }
    }

    /**
     * Sends {@code racer} back if this tick took them off the course, and returns the run they hold afterwards.
     *
     * <p>A racer is sent back when they are outside the vertical bounds of the dimension of the world they stand in,
     * or when they have just landed, and only while their run is unfinished before and after this tick. A finished
     * run is never reset. This is called only from {@link #raceTick}, so it runs in the {@code GAME} phase alone, and
     * only for a racer who holds a run; a racer who is waiting, or who joined mid-race, is never considered.
     *
     * @return {@code advanced}, unless a reset happened, in which case the run the reset left behind
     */
    private RaceRun sendBackIfLostOrLanded(Player racer, MapDefinition map, RaceRun before, RaceRun advanced,
            Vec3 position) {
        if (before.finished() || advanced.finished()) {
            return advanced;
        }
        DimensionType dimension = racer.getInstance().getCachedDimensionType();
        double floor = dimension.minY();
        double ceiling = floor + dimension.height();
        ResetCause cause;
        if (RunReset.isOutOfBounds(position, floor, ceiling)) {
            cause = ResetCause.OUT_OF_BOUNDS;
        } else if (RunReset.isLanded(before, racer.isFlyingWithElytra(), racer.isOnGround())) {
            cause = ResetCause.LANDED;
        } else {
            return advanced;
        }
        ResetPlan plan = RunReset.plan(map, advanced, cause);
        resetter.reset(racer, plan, map.boostConfig());
        tally.recordReset(racer.getUuid());
        LOGGER.info("Racer {} reset for {} to {}", racer.getUsername(), cause, plan.position());
        return plan.run();
    }

    /**
     * Applies the effect of the ring {@code run} was passed on this tick, if that ring has one, to the velocity the
     * server's simulation holds, and sends the result to the racer.
     *
     * <p>Only a gliding racer whose run is unfinished after this tick gets an effect. A STANDARD or CHECKPOINT ring
     * has none, and sends nothing. The velocity written is the simulated one times the ring's factor, capped by the
     * map's speed cap if it states one, and it is written to the simulation as well as the client so the next tick
     * starts from it. A racer whose flight is not simulated has no velocity to multiply, and gets nothing.
     */
    private void applyRingEffect(Player racer, MapDefinition map, RaceRun run) {
        Ring passed = run.justPassed();
        if (passed == null || run.finished() || !racer.isFlyingWithElytra()) {
            return;
        }
        List<RingEffect> effects = ringEffects.effectFor(passed.type()).map(List::of).orElse(List.of());
        if (effects.isEmpty()) {
            return;
        }
        UUID id = racer.getUuid();
        Optional<Vec3> simulated = flight.velocityOf(id);
        if (simulated.isEmpty()) {
            return;
        }
        Vec3 written = RingEffectSequence.apply(simulated.get(), effects,
                map.boostConfig().maxSpeedBlocksPerTick());
        flight.replaceVelocity(id, written);
        VelocityExit.send(racer, written);
    }

    /**
     * One map's tuning in one line: its burn, cooldown, rocket count, speed cap and the two ring factors. Written at
     * the start countdown, so the operator reads what a map is played with before the first racer flies it.
     */
    private static String tuningOf(MapDefinition map) {
        BoostConfig boost = map.boostConfig();
        RingEffectTuning effects = map.ringEffects();
        String cap = boost.maxSpeedBlocksPerTick() == null
                ? "no speed cap"
                : "speed cap %s blocks per tick".formatted(boost.maxSpeedBlocksPerTick());
        return "burn %s, cooldown %s, rockets %s, %s, BOOST %s, SLOW %s".formatted(
                boost.burnDurationTicks(), boost.cooldownTicks(), boost.rocketsPerRacer(), cap,
                effects.boostMultiplier(), effects.slowMultiplier());
    }

    @Override
    public void mapFinished(int mapIndex, String mapName, RaceClock clock) {
        MapDefinition map = currentMap;
        if (map == null) {
            return;
        }
        for (Player racer : players.get()) {
            UUID id = racer.getUuid();
            Optional<RaceRun> held = runs.of(id);
            if (held.isEmpty()) {
                continue;
            }
            // timings.race(), not clock.elapsed(): RaceRun.timeOnCourse scores a run that did not
            // finish on the full phase length, and that has to be the length this phase was played
            // with. A finisher's own time comes out of the run and ignores this argument.
            round.recordMap(mapIndex, id, held.get(), map, timings.race());
            hud.standDown(racer);
            if (!glides.containsKey(id)) {
                if (racer.isFlyingWithElytra() && !held.get().finished()) {
                    startGlide(id, map, mapIndex);
                } else {
                    Racers.standDown(racer);
                }
            }
            // The burn and the cooldown end with the map. A rocket still burning when the next map
            // starts would carry the racer on past its GO, and a cooldown carried across would refuse
            // the first boost of a map for a boost taken on the last.
            boosts.forget(id);
        }
        round.closeMap(mapIndex, cup.mode());
        recordRace(mapIndex, map);

        LOGGER.info("Map {}/{} '{}' finished after {} tick(s), {}",
                mapIndex + 1, cup.mapNames().size(), mapName, clock.gameTick(), CupAnnouncer.seconds(clock.elapsed()));
        for (Player racer : players.get()) {
            announcer.announceMapScore(racer, map, round.scoreOn(racer.getUuid(), mapIndex),
                    ringsPassedOf(racer.getUuid()));
        }
        racing = false;
        // The cup stays on this map until the next map's own lobby prepares it, so the end of this map still names this
        // map. Placements for the next map use its definition directly, so they do not need it prepared yet.
        transition.endMap();
    }

    /**
     * Hands the map that just finished to the recorder: one completion, with every racer who held a run on it.
     *
     * <p>Called after the placement bonus is awarded, so each racer's score is the one the board shows. The
     * recorder is called through a catch: a storage failure is logged here, and the map and the cup go on.
     */
    private void recordRace(int mapIndex, MapDefinition map) {
        try {
            List<RaceParticipant> racers = new ArrayList<>();
            for (Player racer : players.get()) {
                UUID id = racer.getUuid();
                Optional<RaceRun> held = runs.of(id);
                MapScore score = round.scoreOn(id, mapIndex);
                if (held.isEmpty() || score == null) {
                    continue;
                }
                racers.add(new RaceParticipant(id, racer.getUsername(), false, held.get(), score,
                        tally.boosts(id), tally.resets(id), tally.flightTime(id)));
            }
            // A computer racer that ran the map is passed as a computer participant: RaceCompletions leaves it out of the
            // racers and counts it as the bot count. It takes no placement bonus, so its score is the map's score without
            // one, and nothing of that score is stored.
            for (UUID competitor : seatedCompetitors) {
                Optional<RaceRun> held = runs.of(competitor);
                if (held.isEmpty()) {
                    continue;
                }
                MapScore score = MapScorer.score(held.get().progress(), map, held.get().timeOnCourse(timings.race()));
                racers.add(new RaceParticipant(competitor, "COM", true, held.get(), score, 0, 0, Duration.ZERO));
            }
            RaceCompletion completion = RaceCompletions.of(map.key().asString(), LayoutVersion.of(map), cup.mode(), cup.key().asString(),
                    mapStartedAt, clock.instant(), racers);
            if (completion.racers().isEmpty()) {
                // A map nobody raced is not stored: an empty session would only count a map that was never played.
                return;
            }
            recorder.recordRace(completion);
        } catch (RuntimeException failure) {
            LOGGER.warn("Map '{}' was not recorded for statistics: {}", map.key().asString(), failure.getMessage(), failure);
        }
    }

    /**
     * Hands the finished cup to the recorder: one result per standing, best first. Called once, on the tick the cup
     * finishes, and caught in the same way as {@link #recordRace}.
     */
    private void recordCup() {
        try {
            CupCompletion completion = CupCompletions.of(cup.key().asString(), cup.mode(), cupStartedAt, clock.instant(),
                    round.order(), round::of, this::nameOf);
            if (completion.racers().isEmpty()) {
                // A cup nobody raced is not stored, in the same way as a map nobody raced.
                return;
            }
            recorder.recordCup(completion);
        } catch (RuntimeException failure) {
            LOGGER.warn("Cup '{}' was not recorded for statistics: {}", cup.key().asString(), failure.getMessage(), failure);
        }
    }

    /** A placement under way: the map index it is for, the map, and the placement that is moving the racer. */
    private record Arrival(int mapIndex, MapDefinition map, PendingArrival pending) {
    }

    /** A glide-out under way: its curve, the tick it is on, and the map and index it began on. */
    private record Glide(GlideOut glide, int tick, MapDefinition map, int mapIndex) {
    }

    /**
     * The glide step of a tick, run before the phase step: each glide-out under way sends its damped velocity for this
     * tick. Before the phase step, so that a glide-out started on this tick by the phase step first sends on the next tick.
     */
    private void glideStep() {
        tickGlides(onlinePlayers());
    }

    /**
     * The placement step of a tick, run after the phase step: completes the placements under way, starts the ones the wait
     * calls for, and holds each held racer where they are held. It runs in every phase, so placements carry on through the
     * end of a map and into the wait that follows.
     */
    private void placementStep() {
        Map<UUID, Player> online = onlinePlayers();
        tickArrivals(online);
        beginWaitingPlacements(online);
        for (Map.Entry<UUID, Pos> hold : holds.entrySet()) {
            Player racer = online.get(hold.getKey());
            if (racer != null && !isMovingNow(hold.getKey())) {
                Racers.pin(racer, hold.getValue());
            }
        }
    }

    private Map<UUID, Player> onlinePlayers() {
        Map<UUID, Player> online = new HashMap<>();
        for (Player racer : players.get()) {
            online.put(racer.getUuid(), racer);
        }
        return online;
    }

    /**
     * Moves each glide-out on by one tick. Each tick sends the damped velocity of that tick; after the last one the racer
     * is stood down, with the elytra off and no velocity, and held where they stand until placed on the next map. A
     * racer on the last map has no next map, so is stood down and not held.
     */
    private void tickGlides(Map<UUID, Player> online) {
        for (UUID id : List.copyOf(glides.keySet())) {
            Glide glide = glides.get(id);
            Player racer = online.get(id);
            if (racer == null) {
                glides.remove(id);
                continue;
            }
            // Ticks 1 to TICKS each send that tick's damped velocity. The stand-down is on tick TICKS itself, the last tick of
            // the glide, so the racer glides for exactly TICKS ticks and is no longer gliding on the next one.
            int tick = glide.tick() + 1;
            VelocityExit.send(racer, glide.glide().velocityAt(tick));
            if (tick < GlideOut.TICKS) {
                glides.put(id, new Glide(glide.glide(), tick, glide.map(), glide.mapIndex()));
                continue;
            }
            glides.remove(id);
            Racers.standDown(racer);
            LOGGER.debug("Racer {} glided out after the map '{}'", racer.getUsername(), glide.map().key().asString());
            if (followingMapIndex(glide.mapIndex()) != NO_MAP) {
                holds.put(id, racer.getPosition());
                glidedOut.add(id);
            }
        }
    }

    /**
     * Completes or fails each placement under way. A completed placement places the racer: equipped and facing the first
     * ring, held at the spawn until GO, or, when it completes after GO, released to run at once. A failed placement is
     * dropped and the racer is held where they stood; it is tried again at the next wait.
     */
    private void tickArrivals(Map<UUID, Player> online) {
        for (UUID id : List.copyOf(arrivals.keySet())) {
            Arrival arrival = arrivals.get(id);
            Player racer = online.get(id);
            if (racer == null) {
                forgetPlacement(id);
                continue;
            }
            switch (arrival.pending().tick()) {
                case PLACED -> placed(racer, arrival);
                case FAILED -> {
                    arrivals.remove(id);
                    failedPlacement.add(id);
                }
                case WAITING -> {
                }
            }
        }
    }

    /** Whether the racer's placement has begun its move, so the wait no longer holds them where they stood. */
    private boolean isMovingNow(UUID id) {
        Arrival arrival = arrivals.get(id);
        return arrival != null && arrival.pending().isMoving();
    }

    /** Records a racer as placed on the arrival's map, and holds them at its spawn, or starts their run if the race is on. */
    private void placed(Player racer, Arrival arrival) {
        UUID id = racer.getUuid();
        arrivals.remove(id);
        glidedOut.remove(id);
        placedOn.put(id, arrival.mapIndex());
        Racers.faceCourse(racer, arrival.map());
        if (racing && arrival.mapIndex() == preparedMapIndex) {
            // Placed after GO, on the map being raced: no freeze, and the run starts now.
            holds.remove(id);
            runs.startFresh(id);
        } else {
            holds.put(id, ArrivalFacing.at(arrival.map()));
        }
    }

    /**
     * Starts the placement of each racer the current phase calls for: every racer during the lobby and the end of a map,
     * and, during a race, a racer who has glided out after a finish. A racer who is placed, arriving, gliding out or
     * failed in this wait is not started again.
     */
    private void beginWaitingPlacements(Map<UUID, Player> online) {
        XerusPhaseDriver current = driver;
        if (current == null || !current.isRunning()) {
            return;
        }
        RaceState state = current.state();
        int target = placementTarget(state);
        if (target == NO_MAP) {
            return;
        }
        boolean waiting = state.phase() != RacePhase.GAME;
        for (Player racer : online.values()) {
            UUID id = racer.getUuid();
            if (!waiting && !glidedOut.contains(id)) {
                continue;
            }
            if (isPlacedOn(id, target) || arrivals.containsKey(id) || glides.containsKey(id)
                    || failedPlacement.contains(id)) {
                continue;
            }
            begin(racer, target);
        }
    }

    /** Starts the placement of {@code racer} on the map at {@code mapIndex}, holding them where they stand until it completes. */
    private void begin(Player racer, int mapIndex) {
        UUID id = racer.getUuid();
        MapDefinition map = mapAt(mapIndex);
        arrivals.put(id, new Arrival(mapIndex, map, transition.place(racer, map)));
        holds.put(id, racer.getPosition());
        glidedOut.remove(id);
    }

    private void startGlide(UUID id, MapDefinition map, int mapIndex) {
        glides.put(id, new Glide(new GlideOut(lastVelocities.getOrDefault(id, Vec3.ZERO)), 0, map, mapIndex));
    }

    /** The map index racers are placed for in {@code state}'s phase, or {@link #NO_MAP} when none is. */
    private int placementTarget(RaceState state) {
        return switch (state.phase()) {
            case LOBBY -> state.mapIndex();
            case GAME -> cup.mode() == GameMode.PRACTICE ? NO_MAP : followingMapIndex(state.mapIndex());
            case END -> followingMapIndex(state.mapIndex());
        };
    }

    /**
     * The map that follows {@code mapIndex}, or {@link #NO_MAP} after the last one. A practice cup's next map is the same
     * map again, since a practice retry is the same map played again.
     */
    private int followingMapIndex(int mapIndex) {
        if (cup.mode() == GameMode.PRACTICE) {
            return mapIndex;
        }
        return mapIndex + 1 < cup.mapNames().size() ? mapIndex + 1 : NO_MAP;
    }

    private boolean isPlacedOn(UUID id, int mapIndex) {
        Integer placed = placedOn.get(id);
        return placed != null && placed == mapIndex;
    }

    /**
     * Whether a racer is placed on a map other than the one being raced. Such a racer has left this map: the race does
     * not advance their run, draw their HUD or send them this map's line.
     */
    private boolean leftBehind(UUID id) {
        Integer placed = placedOn.get(id);
        return placed != null && placed != preparedMapIndex;
    }

    /** Drops every placement, glide-out and hold, so that no racer is held by a cup that is no longer running. */
    private void clearPlacements() {
        placedOn.clear();
        arrivals.clear();
        holds.clear();
        glides.clear();
        glidedOut.clear();
        failedPlacement.clear();
        lastPositions.clear();
        lastVelocities.clear();
        racing = false;
    }

    private void forgetPlacement(UUID playerId) {
        placedOn.remove(playerId);
        arrivals.remove(playerId);
        holds.remove(playerId);
        glides.remove(playerId);
        glidedOut.remove(playerId);
        failedPlacement.remove(playerId);
        lastPositions.remove(playerId);
        lastVelocities.remove(playerId);
    }

    /** Where the cup stands, in one multi-line block, for {@code /race} and for the boot log. */
    public String describe() {
        XerusPhaseDriver current = driver;
        if (current == null) {
            return "cup '%s' (%s map(s), %s) — armed, waiting for enough racers\n%s"
                    .formatted(cup.key().asString(), cup.mapNames().size(), cup.mode(), catalogueLine());
        }
        StringBuilder text = new StringBuilder();
        text.append(catalogueLine());
        text.append("collision world: %s\n".formatted(blocks.hasWorld() ? "attached" : "none"));
        text.append("cup '%s' %s — map %s/%s '%s', phase %s after %s, race clock %s (%s tick(s))\n".formatted(
                cup.key().asString(),
                current.state().cupFinished() ? "FINISHED" : (current.isRunning() ? "running" : "stopped"),
                current.state().mapIndex() + 1, cup.mapNames().size(),
                currentMap == null ? "-" : currentMap.key().asString(),
                current.state().phase(), CupAnnouncer.seconds(current.state().inPhase()),
                CupAnnouncer.seconds(current.clock().elapsed()), current.clock().gameTick()));
        for (Player racer : players.get()) {
            text.append("  %s\n".formatted(describeRacer(racer)));
        }
        for (CupStanding standing : round.order()) {
            text.append("  cup: %s — %s point(s), %s map(s) finished, best %s\n".formatted(
                    nameOf(standing.playerId()), standing.score().totalPoints(),
                    standing.score().mapsFinished(),
                    standing.score().bestTime().map(CupAnnouncer::seconds).orElse("-")));
        }
        return text.toString();
    }

    /**
     * The catalogue line of {@link #describe()}: when the pinned catalogue was read, and whether a reload is
     * waiting for the next round.
     */
    private String catalogueLine() {
        String line = "catalogue loaded %s\n".formatted(pinned().loadedAt());
        if (catalog.pending().isPresent()) {
            line += "  a reload waits for the next round\n".formatted();
        }
        return line;
    }

    /**
     * Whether the {@code GAME} phase should end on this tick, read once per tick by
     * {@code RaceStateMachine}.
     *
     * <p>The phase guard is here as well as in {@link #requestSkip()}, and deliberately: this supplier
     * is called on every tick of every phase, and {@code RaceStateMachine} ignores the answer outside
     * a {@code GAME} phase. Answering {@code true} during a lobby would therefore be a claim nobody
     * reads today and a defect the day somebody does.
     *
     * <p>{@code driver.state()} is still the previous tick's state when this is called:
     * {@code XerusPhaseDriver} publishes the new one after {@code advance} returns. The previous
     * phase is the right question — it means "a game phase is what we are in" — and it also keeps the
     * transition tick into {@code GAME} from being able to end the phase it has just entered.
     */
    private boolean gamePhaseEndsNow() {
        XerusPhaseDriver current = driver;
        if (current == null || current.state().phase() != RacePhase.GAME) {
            return false;
        }
        if (skipRequested) {
            skipRequested = false;
            return true;
        }
        return runs.everyRacerFinished();
    }

    /**
     * Drops a skip that was asked for and never consumed. Only {@link #start(boolean)} needs it: a
     * skip taken on the map a restart abandons must not end the first map of the new cup.
     */
    private void forgetPendingSkip() {
        skipRequested = false;
    }

    /**
     * One racer's line: where the run stands, whether the client is gliding, where the client says it
     * is, and where the server's own simulation has it. The last pair is the drift, and it is printed
     * rather than asserted because nothing but a real client can produce it.
     */
    private String describeRacer(Player racer) {
        UUID id = racer.getUuid();
        String progress = runs.of(id)
                .map(run -> "ring %s%s".formatted(run.progress().passedCount(), run.finished() ? " (complete)" : ""))
                .orElse("no run");
        FlightTick simulated = lastSimulated.get(id);
        String shadow = simulated == null
                ? "not simulated"
                : "simulated %s v=%s".formatted(simulated.after().position(), simulated.after().velocity());
        return "%s: %s, gliding=%s, boost %s, client %s, %s".formatted(
                racer.getUsername(), progress, racer.isFlyingWithElytra(), describeBoost(id),
                racer.getPosition(), shadow);
    }

    /**
     * A racer's boost state in three words. Worth a place on the line for the same reason
     * {@code collision world:} is: a boost that is never reported to the simulation and a boost that
     * was never asked for look identical from outside, and this is the only way to ask which happened
     * while somebody is flying.
     */
    private String describeBoost(UUID playerId) {
        int burning = boosts.ticksRemaining(playerId);
        if (burning > 0) {
            return "burning %s tick(s)".formatted(burning);
        }
        int cooldown = boosts.cooldownTicksRemaining(playerId);
        return cooldown > 0 ? "cooling down %s tick(s)".formatted(cooldown) : "ready";
    }

    /** How many rings {@code playerId} has passed on the current map, or zero when they hold no run. */
    private int ringsPassedOf(UUID playerId) {
        return runs.of(playerId).map(run -> run.progress().passedCount()).orElse(0);
    }

    /**
     * A racer's name for the diagnostic {@code /race} prints, which is an operator's surface and
     * wants the raw id when there is nothing better.
     */
    private String nameOf(UUID playerId) {
        for (Player racer : players.get()) {
            if (racer.getUuid().equals(playerId)) {
                return racer.getUsername();
            }
        }
        return playerId.toString();
    }

}
