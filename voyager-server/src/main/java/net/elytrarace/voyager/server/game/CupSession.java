package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalBrackets;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.hud.HudState;
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
import net.elytrarace.voyager.race.cup.CupStandings;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.CupScore;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.MapScorer;
import net.elytrarace.voyager.race.scoring.MedalCountdown;
import net.elytrarace.voyager.race.scoring.MedalOutlook;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
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
    private final CupStandings standings = new CupStandings();

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

    private CupSession(CatalogHolder catalog, MapInstances instances, MapTransition transition,
            RaceRuns runs, FlightTickDriver flight, CurrentMapBlocks blocks, FireworkBoostTracker boosts,
            RaceTimings timings, Duration step, Supplier<Collection<Player>> players) {
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
    }

    /**
     * Builds a session and the three objects only it has a use for: the block source the flight
     * simulation reads through, the tracker the burn is counted by, and the sampler that observes
     * live players.
     *
     * <p>A factory rather than a public constructor because two of those three are package-private. They are
     * implementation detail of how a cup is played, not of how one is wired, and keeping them out of
     * the signature keeps the composition root from having to know they exist.
     *
     * @param catalog the holder whose current catalogue each {@link #start} pins
     * @param step the wall-clock duration one {@link #tick()} stands for; 50 ms on a 20 TPS server.
     *     It has to match the interval this is actually ticked at or every phase length and every
     *     recorded race time is scaled by the difference.
     */
    public static CupSession create(CatalogHolder catalog, MapInstances instances,
            MapTransition transition, RaceRuns runs, FlightTracker tracker, RaceTimings timings,
            Duration step, Supplier<Collection<Player>> players) {
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(players, boosts), tracker, new MinestomCollisionSpace(blocks));
        return new CupSession(catalog, instances, transition, runs, flight, blocks, boosts, timings,
                step, players);
    }

    /** The cup being played: the one the current round pinned, or the boot cup before the first round. */
    public CupDefinition cup() {
        return cup;
    }

    /**
     * The catalogue the current round pinned, or the holder's current one before the first round.
     * Package-private: the tests assert the pin, the game asks {@link #cup()}.
     */
    LoadedCatalog pinned() {
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
        standings.clear();
        lastSimulated.clear();
        // A restart owes nobody the abandoned cup's cooldown, and a burn lit on the map it abandons
        // must not still be running when the new first map launches its racers.
        boosts.clear();
        currentMap = null;
        preparedMapIndex = NO_MAP;
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

    /** Advances the whole session by one server tick. */
    public void tick() {
        for (FlightTick simulated : flight.tick()) {
            lastSimulated.put(simulated.playerId(), simulated);
        }
        // After the sample the line above took, never before it — FireworkBoostTracker's javadoc has
        // the reason, and it is an off-by-one nothing downstream can see.
        boosts.advance();
        XerusPhaseDriver current = driver;
        if (current == null) {
            return;
        }
        current.onUpdate();
        if (current.state().cupFinished() && currentMap != null) {
            // mapFinished has already run for the last map by the time cupFinished is set, so every
            // per-map score is in. currentMap is what says this has not been announced yet.
            announceCupResult();
            currentMap = null;
            blocks.follow(null);
        }
    }

    /**
     * The last tick the server's own flight simulation produced for {@code playerId}, if any.
     *
     * <p>Package-private, and it exists for {@code CupSessionTest}: the simulation is silent by
     * design — nothing in a race reads it, it only shadows the client — so the only other trace of it
     * is the line {@link #describe()} renders, and asserting a velocity by parsing a sentence is
     * asserting the sentence. Not part of the session's contract.
     */
    Optional<FlightTick> lastSimulated(UUID playerId) {
        return Optional.ofNullable(lastSimulated.get(playerId));
    }

    /**
     * The board this cup is accumulating into.
     *
     * <p>Package-private, and it exists for {@code CupSessionTest}: the scores are otherwise visible
     * only as the text {@link #describe()} renders and the chat lines a racer is sent, and asserting
     * a medal tier by reading a sentence is asserting the sentence. Not part of the session's
     * contract — do not widen it.
     */
    CupStandings standings() {
        return standings;
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
        HudState armed = startingState(mapIndex, map);
        for (Player racer : players.get()) {
            RaceFeedback.countdown(racer, digit, subtitle);
            hud.arm(racer, armed);
        }
    }

    @Override
    public void mapStarted(int mapIndex, String mapName) {
        MapDefinition map = enterMap(mapIndex, mapName);

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
        broadcast(Messages.mapBanner(mapIndex + 1, cup.mapNames().size(), map.name(),
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
            report(racer, map, advanced, wasFinished, clock);
            hud.render(racer, flightState(map, advanced, clock));
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
            MapScore score = MapScorer.score(held.get().progress(), map, held.get().timeOnCourse(timings.race()));
            standings.record(id, mapIndex, score);
            Racers.standDown(racer);
            hud.standDown(racer);
            // The burn and the cooldown end with the map. A rocket still burning when the next map's
            // launch fires would add an impulse to a launch nothing tuned for one, and a cooldown
            // carried across would refuse the first boost of a map for a boost taken on the last.
            boosts.forget(id);
        }
        standings.closeMap(mapIndex, cup.mode());

        LOGGER.info("Map {}/{} '{}' finished after {} tick(s), {}",
                mapIndex + 1, cup.mapNames().size(), mapName, clock.gameTick(), seconds(clock.elapsed()));
        for (Player racer : players.get()) {
            announceMapScore(racer, map, mapIndex);
        }
    }

    /** Where the cup stands, in one multi-line block, for {@code /race} and for the boot log. */
    public String describe() {
        XerusPhaseDriver current = driver;
        if (current == null) {
            return "cup '%s' (%s map(s), %s) — armed, waiting for the first player to join%n%s"
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
                current.state().phase(), seconds(current.state().inPhase()),
                seconds(current.clock().elapsed()), current.clock().gameTick()));
        for (Player racer : players.get()) {
            text.append("  %s%n".formatted(describeRacer(racer)));
        }
        for (CupStanding standing : standings.cupOrder()) {
            text.append("  cup: %s — %s point(s), %s map(s) finished, best %s%n".formatted(
                    nameOf(standing.playerId()), standing.score().totalPoints(),
                    standing.score().mapsFinished(),
                    standing.score().bestTime().map(CupSession::seconds).orElse("-")));
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
     * What a racer is told in the tick a ring was passed, or the tick their run ended.
     *
     * <p>A ring is a <strong>sound</strong> and a green flash on the counter that is already on
     * screen, and nothing else — no chat line and no title. On this course a racer crosses a ring
     * every 1.4 to 2.1 seconds for a minute, so anything per-ring that costs a <em>read</em> is
     * noise by ring five, and thirty-five chat lines is a wall of text that buries the result that
     * follows it.
     */
    private void report(Player racer, MapDefinition map, RaceRun run, boolean wasFinished, RaceClock clock) {
        Ring passed = run.justPassed();
        if (passed != null) {
            RaceFeedback.ringPassed(racer, passed.index(), map.rings().size());
        }
        if (run.finished() && !wasFinished) {
            racer.sendMessage(Messages.finished(map.name(), clock.elapsed()));
            LOGGER.info("{} finished '{}' on tick {} ({})",
                    racer.getUsername(), map.name(), clock.gameTick(), seconds(clock.elapsed()));
        }
    }

    /**
     * A map's result, to one racer: a title they can read now that they have landed, and a chat line
     * that is still there when the next map starts.
     *
     * <p>The title region is off limits for the whole race — it is exactly where the next ring
     * appears — which is why it is finally worth something here.
     */
    private void announceMapScore(Player racer, MapDefinition map, int mapIndex) {
        MapScore score = standings.scoreOn(racer.getUuid(), mapIndex);
        if (score == null) {
            return;
        }
        int ringCount = map.rings().size();
        int ringsPassed = runs.of(racer.getUuid()).map(run -> run.progress().passedCount()).orElse(0);
        if (score.medal() == MedalTier.DNF) {
            racer.sendMessage(Messages.mapResultDnf(map.name(), ringsPassed, ringCount, score.total()));
            RaceFeedback.mapResult(racer, MedalTier.DNF,
                    Messages.mapResultSubtitleDnf(ringsPassed, ringCount, score.total()));
            return;
        }
        racer.sendMessage(Messages.mapResult(map.name(), score.ringPoints(), score.medalPoints(),
                score.medal(), score.placementBonus(), score.total()));
        RaceFeedback.mapResult(racer, score.medal(), Messages.mapResultSubtitle(
                score.completionTime().orElse(clockLengthOf(map)), score.total()));
    }

    /**
     * The cup's final standings: the block everybody sees, and one title each saying where they
     * came.
     */
    private void announceCupResult() {
        List<CupStanding> order = standings.cupOrder();
        LOGGER.info("Cup '{}' finished with {} classified racer(s)", cup.name(), order.size());
        broadcast(Messages.cupHeading(cup.name()));
        int mapCount = cup.mapNames().size();
        int place = 1;
        for (CupStanding standing : order) {
            CupScore score = standing.score();
            LOGGER.info("  {}. {} — {} point(s), {} map(s) finished, best {}",
                    place, standing.playerId(), score.totalPoints(), score.mapsFinished(),
                    score.bestTime().map(CupSession::seconds).orElse("-"));
            broadcast(Messages.cupRow(place, displayName(standing.playerId()), score.totalPoints(),
                    score.mapsFinished(), mapCount, score.bestTime()));
            announceCupPlace(standing.playerId(), place, score.totalPoints());
            place++;
        }
    }

    /** The cup title, to the one racer it names, if they are still online to see it. */
    private void announceCupPlace(UUID playerId, int place, int points) {
        for (Player racer : players.get()) {
            if (racer.getUuid().equals(playerId)) {
                RaceFeedback.cupResult(racer, Messages.cupSubtitle(place, points));
                return;
            }
        }
    }

    /**
     * The HUD value for a racer mid-flight.
     *
     * <p>A racer who has already crossed the last ring is shown the clock they <em>finished</em> on,
     * not the one still running: their medal is decided and a boss bar counting down a band they can
     * no longer lose would be counting nothing.
     */
    private HudState flightState(MapDefinition map, RaceRun run, RaceClock clock) {
        Duration elapsed = run.finishedAt().map(RaceClock::elapsed).orElse(clock.elapsed());
        List<Integer> ringTicks = run.passedOnGameTick();
        int lastRingTick = ringTicks.isEmpty() ? 0 : ringTicks.getLast();
        return new HudState(clock.gameTick(), elapsed, run.progress().passedCount(), map.rings().size(),
                lastRingTick, preparedMapIndex + 1, cup.mapNames().size(), map.name(),
                outlookAt(elapsed, map));
    }

    /** The HUD value shown during the start countdown: nothing flown yet, and the best medal on offer. */
    private HudState startingState(int mapIndex, MapDefinition map) {
        return new HudState(0, Duration.ZERO, 0, map.rings().size(), 0, mapIndex + 1,
                cup.mapNames().size(), map.name(), outlookAt(Duration.ZERO, map));
    }

    /**
     * {@code MedalBrackets.DEFAULT}, which is the same constant {@code MapScorer} classifies a
     * finished run with — so the band the boss bar showed on the last tick of a race and the medal
     * the results screen awards cannot disagree.
     */
    private static MedalOutlook outlookAt(Duration elapsed, MapDefinition map) {
        return MedalCountdown.outlook(elapsed, map.referenceTime(), MedalBrackets.DEFAULT);
    }

    /**
     * The time to print for a score that somehow carries no completion time on a non-DNF medal.
     *
     * <p>{@code MapScorer} always records one for a finisher, so this is an assertion rather than a
     * branch anybody takes; the map's reference time is the least misleading thing to fall back on.
     */
    private static Duration clockLengthOf(MapDefinition map) {
        return map.referenceTime();
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

    /**
     * A racer's name for the standings a player reads.
     *
     * <p>Somebody who disconnected before the cup ended is {@code (left)}, not 36 characters of
     * hexadecimal in the middle of a results table. The UUID is still in the log line beside it,
     * where somebody debugging can use it and nobody else has to read it.
     */
    private Component displayName(UUID playerId) {
        for (Player racer : players.get()) {
            if (racer.getUuid().equals(playerId)) {
                return Messages.racerName(racer.getUsername());
            }
        }
        return Messages.departed();
    }

    private void broadcast(Component message) {
        for (Player racer : new ArrayList<>(players.get())) {
            racer.sendMessage(message);
        }
    }

    private static String seconds(Duration duration) {
        return "%.3f s".formatted(duration.toNanos() / 1_000_000_000.0);
    }
}
