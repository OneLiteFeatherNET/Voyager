package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.hud.HudState;
import net.elytrarace.voyager.platform.hud.HudStates;
import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.elytrarace.voyager.platform.hud.RaceHud;
import net.elytrarace.voyager.platform.hud.StartCountdown;
import net.elytrarace.voyager.platform.render.GuideLineRenderer;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.tick.FlightTick;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.tick.RacePhaseListener;
import net.elytrarace.voyager.platform.tick.XerusPhaseDriver;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.platform.world.WorldHealth;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.cup.CupStanding;
import net.elytrarace.voyager.race.cup.CupRound;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.flow.StartGate;
import net.elytrarace.voyager.race.cup.MapFigures;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.flight.Rockets;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>A player who joins while a cup is running is not moved and holds no run until the next map
 * starts — {@code MapTransition.advanceTo} takes the players it is given, and it is given them only
 * when a map begins. That is behaviour inherited from Task 7b's design rather than a decision taken
 * here, and it is the second reason {@code /race start} exists.
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
     * Builds a session from the collaborators the composition root wires. The flight driver samples the players
     * through the boost tracker and reads blocks through the block source, so those three belong together; the
     * composition root builds them as one graph.
     *
     * @param catalog the holder whose current catalogue each {@link #start} pins
     * @param step the wall-clock duration one {@link #tick()} stands for; 50 ms on a 20 TPS server.
     *     It has to match the interval this is actually ticked at or every phase length and every
     *     recorded race time is scaled by the difference.
     */
    public CupSession(CatalogHolder catalog, MapInstances instances,
            MapTransition transition, RaceRuns runs, FlightTickDriver flight, CurrentMapBlocks blocks,
            FireworkBoostTracker boosts, RaceTimings timings, Duration step, Supplier<Collection<Player>> players) {
        this.catalog = catalog;
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
        lastSimulated.clear();
        // A restart owes nobody the abandoned cup's cooldown, and a burn lit on the map it abandons
        // must not still be running when the new first map launches its racers.
        boosts.clear();
        currentMap = null;
        preparedMapIndex = NO_MAP;
        gameStarted = false;
        countdown.reset();
        for (Player racer : players.get()) {
            hud.standDown(racer);
        }
        forgetPendingSkip();
        RaceTimings played = skipLobby
                ? new RaceTimings(Duration.ZERO, timings.race(), timings.endBetweenMaps(),
                        timings.endAfterLastMap())
                : timings;
        XerusPhaseDriver next = new XerusPhaseDriver(cup, played, step, this::gamePhaseEndsNow, this);
        driver = next;
        next.start();
        LOGGER.info("Cup '{}' started: {} map(s), mode {}, lobby {}, race {}, results {} between maps "
                        + "and {} after the last", cup.name(), cup.mapNames().size(), cup.mode(),
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
        round.reset();
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
        current.onUpdate();
        if (current.state().cupFinished() && currentMap != null) {
            // mapFinished has already run for the last map by the time cupFinished is set, so every
            // per-map score is in. currentMap is what says this has not been announced yet.
            announcer.announceCupResult(cup, round.order());
            currentMap = null;
            blocks.follow(null);
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
        Rockets.fire(racer, config);
        return true;
    }

    /**
     * The start countdown, run out of the tail of the lobby.
     *
     * <p>The map is entered on the first tick that has a digit to show — three seconds before the
     * launch — so the racer is standing on the spawn, facing the first ring, with the chunks around
     * them already there and the boss bar already naming the target, while the count runs. Before
     * this, a map began by teleporting a player and firing them into the air in the same tick, into a
     * world whose chunks were still arriving.
     *
     * <p>Nothing here touches the race clock. The {@code GAME} phase still begins with the launch on
     * its first tick, and the three seconds come out of the lobby, which is time nobody was playing
     * in anyway.
     */
    @Override
    public void lobbyTick(int mapIndex, String mapName, Duration remaining) {
        if (preparedMapIndex != mapIndex) {
            countdown.reset();
        }
        int digit = countdown.show(remaining);
        if (digit == StartCountdown.NONE) {
            return;
        }
        MapDefinition map = enterMap(mapIndex, mapName);
        Component subtitle = Messages.countdownSubtitle(
                map.name(), map.rings().size(), map.referenceTime());
        HudState armed = HudStates.of(MapFigures.starting(map, mapIndex + 1, cup.mapNames().size()));
        for (Player racer : players.get()) {
            RaceFeedback.countdown(racer, digit, subtitle);
            hud.arm(racer, armed);
        }
    }

    @Override
    public void mapStarted(int mapIndex, String mapName) {
        MapDefinition map = enterMap(mapIndex, mapName);
        gameStarted = true;

        // The transition runs again here even when the countdown already made it, and that is
        // deliberate: a player who connected during those three seconds holds no run, and a launch
        // into a race that is not tracking you is the exact failure this whole start exists to
        // remove. Nobody has moved and no race tick has been played since, so re-running it lands
        // every racer on the same spawn with the same fresh run.
        List<Player> racers = List.copyOf(players.get());
        transition.advanceTo(map, racers);
        for (Player racer : racers) {
            Racers.launch(racer, map);
            RaceFeedback.go(racer);
        }

        WorldHealth health = instances.healthOf(map.world());
        LOGGER.info("Map {}/{} '{}' started on world '{}' with {} racer(s) — {}",
                mapIndex + 1, cup.mapNames().size(), map.name(), map.world(), racers.size(), health.describe());
        if (!health.isSound()) {
            LOGGER.warn("World '{}' is not sound: {}", map.world(), health.describe());
        }
    }

    /**
     * Moves the cup onto {@code mapIndex}: the world, the block source, the racers and the chat
     * banner, once.
     *
     * <p>Called by whichever of the two gets there first. With a lobby the countdown does it three
     * seconds early; with no lobby — {@code /race start}, or a dev run with the lobby skipped — there
     * is no lobby tick to do it in and {@link #mapStarted} does it on the launch tick instead. A dev
     * flag that reintroduced a three-second wait would defeat its own purpose, so the countdown
     * degrades rather than delays, and this is the seam that lets it.
     */
    private MapDefinition enterMap(int mapIndex, String mapName) {
        MapDefinition held = currentMap;
        if (preparedMapIndex == mapIndex && held != null) {
            return held;
        }
        MapDefinition map = pinned().snapshot().mapByName(mapName).orElseThrow(() -> new IllegalStateException(
                ("cup '%s' plays a map named '%s' that the round's catalogue does not hold; "
                        + "CatalogReloader checks the played cup's maps and should have refused this cup")
                        .formatted(cup.name(), mapName)));
        currentMap = map;
        preparedMapIndex = mapIndex;
        Instance instance = instances.forWorld(map.world());
        blocks.follow(instance);

        List<Player> racers = List.copyOf(players.get());
        transition.advanceTo(map, racers);
        for (Player racer : racers) {
            Racers.faceCourse(racer, map);
        }
        announcer.broadcast(Messages.mapBanner(mapIndex + 1, cup.mapNames().size(), map.name(),
                map.rings().size(), map.referenceTime()));
        return map;
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
            boolean wasFinished = held.get().finished();
            RaceRun advanced = runs.advance(id, map, clock,
                    Vectors.toDomain(racer.getPosition()), racer.isFlyingWithElytra());
            announcer.report(racer, map, advanced, wasFinished, clock);
            hud.render(racer, HudStates.of(MapFigures.inFlight(advanced, clock, map, preparedMapIndex + 1,
                    cup.mapNames().size())));
            // After the advance, not before it: a racer who passed a ring on this tick is heading for
            // the next one from this tick, and showing them the stretch they have just flown out of
            // for another four ticks is the one moment the line would be visibly wrong.
            lines.render(racer, map, advanced.progress().passedCount(), clock.gameTick());
        }
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
            Racers.standDown(racer);
            hud.standDown(racer);
            // The burn and the cooldown end with the map. A rocket still burning when the next map's
            // launch fires would add an impulse to a launch nothing tuned for one, and a cooldown
            // carried across would refuse the first boost of a map for a boost taken on the last.
            boosts.forget(id);
        }
        round.closeMap(mapIndex, cup.mode());

        LOGGER.info("Map {}/{} '{}' finished after {} tick(s), {}",
                mapIndex + 1, cup.mapNames().size(), mapName, clock.gameTick(), CupAnnouncer.seconds(clock.elapsed()));
        for (Player racer : players.get()) {
            announcer.announceMapScore(racer, map, round.scoreOn(racer.getUuid(), mapIndex),
                    ringsPassedOf(racer.getUuid()));
        }
    }

    /** Where the cup stands, in one multi-line block, for {@code /race} and for the boot log. */
    public String describe() {
        XerusPhaseDriver current = driver;
        if (current == null) {
            return "cup '%s' (%s map(s), %s) — armed, waiting for enough racers%n%s"
                    .formatted(cup.name(), cup.mapNames().size(), cup.mode(), catalogueLine());
        }
        StringBuilder text = new StringBuilder();
        text.append(catalogueLine());
        text.append("collision world: %s%n".formatted(blocks.hasWorld() ? "attached" : "none"));
        text.append("cup '%s' %s — map %s/%s '%s', phase %s after %s, race clock %s (%s tick(s))%n".formatted(
                cup.name(),
                current.state().cupFinished() ? "FINISHED" : (current.isRunning() ? "running" : "stopped"),
                current.state().mapIndex() + 1, cup.mapNames().size(),
                currentMap == null ? "-" : currentMap.name(),
                current.state().phase(), CupAnnouncer.seconds(current.state().inPhase()),
                CupAnnouncer.seconds(current.clock().elapsed()), current.clock().gameTick()));
        for (Player racer : players.get()) {
            text.append("  %s%n".formatted(describeRacer(racer)));
        }
        for (CupStanding standing : round.order()) {
            text.append("  cup: %s — %s point(s), %s map(s) finished, best %s%n".formatted(
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
        String line = "catalogue loaded %s%n".formatted(pinned().loadedAt());
        if (catalog.pending().isPresent()) {
            line += "  a reload waits for the next round%n".formatted();
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
