package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.flight.FlightTracker;
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
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.run.RaceRun;
import net.elytrarace.voyager.race.scoring.CupScore;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.MapScorer;
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
 * assumption. They will diverge whenever a firework is burning, because {@code LivePlayerSampler}
 * does not yet report a boost; that is E5's, and it is a known gap rather than a surprise.
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

    private final CupDefinition cup;
    private final MapCatalog maps;
    private final MapInstances instances;
    private final MapTransition transition;
    private final RaceRuns runs;
    private final FlightTickDriver flight;
    private final CurrentMapBlocks blocks;
    private final RaceTimings timings;
    private final Duration step;
    private final Supplier<Collection<Player>> players;
    private final CupStandings standings = new CupStandings();

    /** The last simulated tick per player, kept only so {@link #describe()} can show the drift. */
    private final Map<UUID, FlightTick> lastSimulated = new HashMap<>();

    private @Nullable XerusPhaseDriver driver;
    private @Nullable MapDefinition currentMap;
    private boolean skipRequested;

    private CupSession(CupDefinition cup, MapCatalog maps, MapInstances instances, MapTransition transition,
            RaceRuns runs, FlightTickDriver flight, CurrentMapBlocks blocks, RaceTimings timings,
            Duration step, Supplier<Collection<Player>> players) {
        this.cup = cup;
        this.maps = maps;
        this.instances = instances;
        this.transition = transition;
        this.runs = runs;
        this.flight = flight;
        this.blocks = blocks;
        this.timings = timings;
        this.step = step;
        this.players = players;
    }

    /**
     * Builds a session and the two objects only it has a use for: the block source the flight
     * simulation reads through, and the sampler that observes live players.
     *
     * <p>A factory rather than a public constructor because those two are package-private. They are
     * implementation detail of how a cup is played, not of how one is wired, and keeping them out of
     * the signature keeps the Guice module from having to know they exist.
     *
     * @param step the wall-clock duration one {@link #tick()} stands for; 50 ms on a 20 TPS server.
     *     It has to match the interval this is actually ticked at or every phase length and every
     *     recorded race time is scaled by the difference.
     */
    public static CupSession create(CupDefinition cup, MapCatalog maps, MapInstances instances,
            MapTransition transition, RaceRuns runs, FlightTracker tracker, RaceTimings timings,
            Duration step, Supplier<Collection<Player>> players) {
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(players), tracker, new MinestomCollisionSpace(blocks));
        return new CupSession(cup, maps, instances, transition, runs, flight, blocks, timings, step, players);
    }

    /** The cup being played. */
    public CupDefinition cup() {
        return cup;
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
        standings.clear();
        lastSimulated.clear();
        currentMap = null;
        forgetPendingSkip();
        RaceTimings played = skipLobby
                ? new RaceTimings(Duration.ZERO, timings.race(), timings.end())
                : timings;
        XerusPhaseDriver next = new XerusPhaseDriver(cup, played, step, this::gamePhaseEndsNow, this);
        driver = next;
        next.start();
        LOGGER.info("Cup '{}' started: {} map(s), mode {}, lobby {}, race {}, results {}",
                cup.name(), cup.mapNames().size(), cup.mode(), played.lobby(), played.race(), played.end());
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
        lastSimulated.remove(playerId);
    }

    @Override
    public void mapStarted(int mapIndex, String mapName) {
        MapDefinition map = maps.byName(mapName).orElseThrow(() -> new IllegalStateException(
                ("cup '%s' plays a map named '%s' that the map catalogue does not hold; "
                        + "CatalogConsistency.requireEveryCupMapResolves runs at boot and should have "
                        + "refused this cup").formatted(cup.name(), mapName)));
        currentMap = map;
        Instance instance = instances.forWorld(map.world());
        blocks.follow(instance);

        List<Player> racers = List.copyOf(players.get());
        transition.advanceTo(map, racers);
        for (Player racer : racers) {
            Racers.launch(racer, map);
        }

        WorldHealth health = instances.healthOf(map.world());
        LOGGER.info("Map {}/{} '{}' started on world '{}' with {} racer(s) — {}",
                mapIndex + 1, cup.mapNames().size(), map.name(), map.world(), racers.size(), health.describe());
        if (!health.isSound()) {
            LOGGER.warn("World '{}' is not sound: {}", map.world(), health.describe());
        }
        broadcast(Component.text("Map %s/%s: %s — %s rings, reference %s".formatted(
                mapIndex + 1, cup.mapNames().size(), map.name(), map.rings().size(),
                seconds(map.referenceTime()))));
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
        }
        standings.closeMap(mapIndex, cup.mode());

        LOGGER.info("Map {}/{} '{}' finished after {} tick(s), {}",
                mapIndex + 1, cup.mapNames().size(), mapName, clock.gameTick(), seconds(clock.elapsed()));
        for (Player racer : players.get()) {
            announceMapScore(racer, mapIndex);
        }
    }

    /** Where the cup stands, in one multi-line block, for {@code /race} and for the boot log. */
    public String describe() {
        XerusPhaseDriver current = driver;
        if (current == null) {
            return "cup '%s' (%s map(s), %s) — armed, waiting for the first player to join"
                    .formatted(cup.name(), cup.mapNames().size(), cup.mode());
        }
        StringBuilder text = new StringBuilder();
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

    private void report(Player racer, MapDefinition map, RaceRun run, boolean wasFinished, RaceClock clock) {
        Ring passed = run.justPassed();
        if (passed != null) {
            racer.sendActionBar(Component.text("Ring %s/%s  +%s  %s".formatted(
                    run.progress().passedCount(), map.rings().size(), passed.points(), seconds(clock.elapsed()))));
        }
        if (run.finished() && !wasFinished) {
            racer.sendMessage(Component.text("Finished %s in %s".formatted(map.name(), seconds(clock.elapsed()))));
            LOGGER.info("{} finished '{}' on tick {} ({})",
                    racer.getUsername(), map.name(), clock.gameTick(), seconds(clock.elapsed()));
        }
    }

    private void announceMapScore(Player racer, int mapIndex) {
        MapScore score = standings.scoreOn(racer.getUuid(), mapIndex);
        if (score == null) {
            return;
        }
        racer.sendMessage(Component.text(
                "Map %s: %s point(s) — %s from rings, %s for %s, %s for placement".formatted(
                        mapIndex + 1, score.total(), score.ringPoints(), score.medalPoints(),
                        score.medal(), score.placementBonus())));
    }

    private void announceCupResult() {
        List<CupStanding> order = standings.cupOrder();
        LOGGER.info("Cup '{}' finished with {} classified racer(s)", cup.name(), order.size());
        broadcast(Component.text("Cup '%s' finished".formatted(cup.name())));
        int place = 1;
        for (CupStanding standing : order) {
            CupScore score = standing.score();
            String line = "%s. %s — %s point(s), %s map(s) finished, best %s".formatted(
                    place, nameOf(standing.playerId()), score.totalPoints(), score.mapsFinished(),
                    score.bestTime().map(CupSession::seconds).orElse("-"));
            LOGGER.info("  {}", line);
            broadcast(Component.text(line));
            place++;
        }
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
        return "%s: %s, gliding=%s, client %s, %s".formatted(
                racer.getUsername(), progress, racer.isFlyingWithElytra(), racer.getPosition(), shadow);
    }

    private String nameOf(UUID playerId) {
        for (Player racer : players.get()) {
            if (racer.getUuid().equals(playerId)) {
                return racer.getUsername();
            }
        }
        return playerId.toString();
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
