package net.elytrarace.voyager.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.CupCatalog;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.race.effect.RingEffect;
import net.elytrarace.voyager.race.collision.RingPass;
import net.elytrarace.voyager.race.effect.RingEffectRegistry;
import net.elytrarace.voyager.race.flow.RacePhase;
import net.elytrarace.voyager.race.flow.RaceState;
import net.elytrarace.voyager.race.flow.RaceStateMachine;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.progress.ProgressTracker;
import net.elytrarace.voyager.race.progress.ProgressUpdate;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.scoring.CupScore;
import net.elytrarace.voyager.race.scoring.CupScorer;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.elytrarace.voyager.race.scoring.MapScorer;
import net.elytrarace.voyager.race.scoring.Placement;
import net.elytrarace.voyager.race.scoring.PlacementBonus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The E3 stage gate: a whole two-map cup, three simulated racers, played end to end inside one JUnit
 * run with no server anywhere.
 *
 * <p>The old tree's equivalent — {@code GameOrchestratorTest} — was committed already disabled, with
 * the note "Requires full Minestom player support - run manually", and has never run. That is exactly
 * how it stayed unnoticed there that a cup only ever races its first map. Everything below is driven
 * by plain loops over pure functions, so there is nothing to run manually.
 *
 * <h2>How the harness works</h2>
 *
 * <p>{@link RaceStateMachine} is stepped one 50 ms tick at a time. On every tick whose resulting phase
 * is {@link RacePhase#GAME}, each racer is placed at a scripted position and {@link ProgressTracker}
 * is asked what that tick's movement passed. Each racer flies a fixed lane — constant {@code x} and
 * {@code y}, {@code z} advancing at a per-map speed from the map's own spawn {@code z} — so every ring
 * crossing is a closed-form consequence of the fixture and can be recomputed by hand:
 *
 * <pre>
 *   crossing z  = ring.cz + (nx * (ring.cx - laneX) + ny * (ring.cy - laneY)) / nz
 *   deviation   = |(laneX, laneY, crossing z) - ring.center|        must be &lt;= ring.radius
 *   game tick   = ceil((crossing z - map.spawn().z()) / blocksPerTick)
 * </pre>
 *
 * <p>That the lateral term is non-zero at all is the point of the tilted normals: with a normal of
 * {@code (0, 0, 1)} two of the three dot-product terms vanish and the crossing would sit exactly on
 * the ring's own {@code z}, which is how the old tree's normal handling escaped ever being exercised
 * by its own shipped maps. Only one normal in this fixture has a zero component pattern that simple,
 * and none is axis-aligned.
 *
 * <h2>Why the fixture values are what they are</h2>
 *
 * <p>Every quantity that two different pieces of production code could read is deliberately given
 * different values, so that code reading the wrong one is distinguishable from code reading the right
 * one:
 *
 * <ul>
 *   <li>Five rings on map one, four on map two; three racers; two maps — 5, 4, 3, 2 all distinct, so
 *       no "ring count" can be confused with a player count, a map count or a map index.
 *   <li>Reference times 3 s and 5 s, so a scorer that hardcoded one map's reference lands on the
 *       wrong medal for the other.
 *   <li>Each {@link Ring#points()} is a distinct value, none of them is 10, and no leading run of
 *       them sums to ten times its length: map one pays 113 for all five rings and 18 for its first
 *       two, map two 114 for all four and 17 for its first one. A scorer that paid a flat 10 a ring
 *       — which is what this one did until the final review — would produce 50, 20, 40 and 10
 *       instead, and every one of those differs.
 *   <li>Every ring radius differs, so a pass checked against a neighbouring ring's radius shows up.
 *   <li>Per-map speeds differ per racer, so nobody's fastest map is decided by a single speed.
 *   <li>Which racer fails to finish rotates between the two maps, so the placement order on map two
 *       is <em>not</em> the placement order on map one — a bonus carried over from the previous map
 *       rather than recomputed would be visible.
 *   <li>The best time of the cup comes from map two for Rook and from map one for Wren, so
 *       {@link CupScore#bestTime()} cannot be right by accidentally always being the first map's.
 *   <li>{@code mapsFinished} is 2, 1 and 1 — not equal across racers, and not equal to the map count
 *       for all of them, so neither {@code mapNames().size()} nor a map index could stand in for it.
 * </ul>
 */
class CupPlaythroughTest {

    // ------------------------------------------------------------------------------------------
    // Clock
    // ------------------------------------------------------------------------------------------

    /** One Minecraft tick at 20 TPS. */
    private static final Duration TICK = Duration.ofMillis(50);

    /**
     * Lobby 2 s, race 8 s, end 1 s — all three different, so a machine reading the wrong
     * {@link RaceTimings} field could not pass. In ticks: 40, 160, 20.
     */
    private static final RaceTimings TIMINGS =
            new RaceTimings(Duration.ofSeconds(2), Duration.ofSeconds(8), Duration.ofSeconds(1));

    private static final int LOBBY_TICKS = 40;
    private static final int GAME_TICKS = 160;
    private static final int END_TICKS = 20;

    /** 2 maps x (40 + 160 + 20) ticks. Asserted, not assumed — see {@link #theCupRunsToItsOwnEnd()}. */
    private static final int EXPECTED_TICKS = 440;

    /** Generous upper bound; a machine that never terminates fails on the assertion, not by hanging. */
    private static final int TICK_BUDGET = 5_000;

    private static final int NOT_FINISHED = -1;

    // ------------------------------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------------------------------

    private static final double TWO_THIRDS = 2.0 / 3.0;
    private static final double ONE_THIRD = 1.0 / 3.0;

    /**
     * Map one: five rings, a 3 s reference time, spawn at {@code z = 0}. Normals are tilted in x, in
     * y, in both, and negatively in x across the course; two of them have all three components
     * non-zero.
     */
    private static final MapDefinition EMBER_ASCENT = new MapDefinition("ember-ascent", "ember_arena",
            new Vec3(0.5, 64.0, 0.0),
            List.of(
                    new Ring(0, new Vec3(0.0, 64.0, 31.0), new Vec3(0.6, 0.0, 0.8), 6.0, 7, RingType.STANDARD),
                    new Ring(1, new Vec3(2.0, 66.0, 62.0), new Vec3(0.0, 0.6, 0.8), 7.0, 11, RingType.BOOST),
                    new Ring(2, new Vec3(-1.0, 65.0, 93.0), new Vec3(TWO_THIRDS, ONE_THIRD, TWO_THIRDS), 8.0, 21,
                            RingType.CHECKPOINT),
                    new Ring(3, new Vec3(1.0, 67.0, 121.0), new Vec3(0.48, 0.64, 0.6), 9.0, 33, RingType.STANDARD),
                    new Ring(4, new Vec3(-1.5, 63.5, 152.0), new Vec3(-0.36, 0.48, 0.8), 10.0, 41, RingType.BOOST)),
            Duration.ofSeconds(3));

    /**
     * Map two: four rings, a 5 s reference time, spawn at {@code z = 3} — a different start line, so
     * anything that assumed the course begins at {@code z = 0} shifts every crossing tick here.
     */
    private static final MapDefinition GLACIER_CHICANE = new MapDefinition("glacier-chicane", "glacier_arena",
            new Vec3(-1.5, 61.5, 3.0),
            List.of(
                    new Ring(0, new Vec3(1.0, 63.0, 48.0), new Vec3(-0.6, 0.0, 0.8), 7.5, 17, RingType.CHECKPOINT),
                    new Ring(1, new Vec3(-2.0, 66.0, 110.0), new Vec3(ONE_THIRD, -TWO_THIRDS, TWO_THIRDS), 8.5, 23,
                            RingType.SLOW),
                    new Ring(2, new Vec3(2.5, 64.5, 168.0), new Vec3(0.8, 0.0, 0.6), 9.5, 31, RingType.STANDARD),
                    new Ring(3, new Vec3(0.5, 65.5, 223.0), new Vec3(0.36, -0.48, 0.8), 11.0, 43, RingType.STANDARD)),
            Duration.ofSeconds(5));

    private static final String CUP_NAME = "frostfire-cup";

    private static final CupDefinition FROSTFIRE_CUP = new CupDefinition(CUP_NAME,
            List.of(EMBER_ASCENT.name(), GLACIER_CHICANE.name()), GameMode.RACE);

    /**
     * The in-memory catalogs the ports were designed for: {@code voyager-race} is handed a catalog
     * rather than loading one, so a whole cup resolves here without Gson or a filesystem.
     */
    private static final MapCatalog MAP_CATALOG = name -> List.of(EMBER_ASCENT, GLACIER_CHICANE).stream()
            .filter(map -> map.name().equals(name))
            .findFirst();

    private static final CupCatalog CUP_CATALOG =
            name -> CUP_NAME.equals(name) ? Optional.of(FROSTFIRE_CUP) : Optional.empty();

    private static final RingEffectRegistry EFFECTS = RingEffectRegistry.create();

    /**
     * The velocity a racer notionally carries into each map. 4.0 is chosen so that both multipliers
     * land on exactly representable doubles: 4 x 1.5 = 6, 6 x 1.5 = 9, 4 x 0.5 = 2.
     *
     * <p>Effects are applied to this vector but deliberately <em>not</em> fed back into the scripted
     * path — the whole point of a scripted path is that every crossing tick is hand-checkable. What is
     * under test here is that the domain picks the right effect for the ring type it just passed.
     */
    private static final Vec3 NOMINAL_VELOCITY = new Vec3(0.0, 0.0, 4.0);

    // ------------------------------------------------------------------------------------------
    // Racers
    // ------------------------------------------------------------------------------------------

    /** A speed high enough that no racer is ever stopped by geometry rather than by the script. */
    private static final int ALWAYS_GLIDING = Integer.MAX_VALUE;

    private static final Racer ROOK =
            new Racer("Rook", 1.5, 65.0, List.of(2.65, 4.40), List.of(ALWAYS_GLIDING, ALWAYS_GLIDING));

    /** Finishes map one; closes the elytra on map two after a single ring and drifts the rest. */
    private static final Racer WREN =
            new Racer("Wren", -2.0, 63.0, List.of(2.10, 2.15), List.of(ALWAYS_GLIDING, 1));

    /** Closes the elytra on map one after two of five rings and drifts; finishes map two. */
    private static final Racer PIKE =
            new Racer("Pike", 0.75, 66.5, List.of(2.00, 2.11), List.of(2, ALWAYS_GLIDING));

    private static final List<Racer> RACERS = List.of(ROOK, WREN, PIKE);

    // ------------------------------------------------------------------------------------------
    // What the playthrough recorded
    // ------------------------------------------------------------------------------------------

    private static final List<Integer> MAP_INDICES_THAT_REACHED_GAME = new ArrayList<>();
    private static final List<Result> RESULTS = new ArrayList<>();

    private static Map<String, CupScore> cupScores;
    private static RaceState finalState;
    private static int ticksSimulated;
    private static boolean everyRacerFinishedWasSignalled;

    // ------------------------------------------------------------------------------------------
    // The playthrough
    // ------------------------------------------------------------------------------------------

    @BeforeAll
    static void playTheWholeCup() {
        CupDefinition cup = CUP_CATALOG.byName(CUP_NAME).orElseThrow();

        RaceState state = RaceState.initial();
        MapDefinition activeMap = null;
        int activeMapIndex = NOT_FINISHED;
        int gameTick = 0;
        Map<String, Run> runs = Map.of();
        int tick = 0;

        while (!state.cupFinished() && tick < TICK_BUDGET) {
            tick++;

            // Computed from the state at the end of the previous tick, which is what a real driver
            // would have. It never becomes true in this playthrough — somebody always fails to
            // finish — so GAME always ends on its 8 s limit. That is what makes the recorded finish
            // times falsifiable: a time taken from the end of the phase would read 8.000 s for
            // everyone.
            boolean everyRacerFinished = activeMap != null && everyRacerFinished(runs, activeMap);
            everyRacerFinishedWasSignalled = everyRacerFinishedWasSignalled || everyRacerFinished;

            RaceState next = RaceStateMachine.advance(state, cup, TIMINGS, TICK, everyRacerFinished);

            if (next.phase() == RacePhase.GAME && state.phase() != RacePhase.GAME) {
                activeMapIndex = next.mapIndex();
                activeMap = MAP_CATALOG.byName(cup.mapNames().get(activeMapIndex)).orElseThrow();
                MAP_INDICES_THAT_REACHED_GAME.add(activeMapIndex);
                gameTick = 0;
                runs = freshRuns();
            }
            if (next.phase() == RacePhase.GAME) {
                gameTick++;
                flyOneTick(activeMap, activeMapIndex, gameTick, runs);
            }
            if (state.phase() == RacePhase.GAME && next.phase() != RacePhase.GAME) {
                scoreTheMap(activeMapIndex, activeMap, cup.mode(), runs);
                activeMap = null;
            }

            state = next;
        }

        finalState = state;
        ticksSimulated = tick;
        cupScores = accumulateCupScores();
    }

    private static Map<String, Run> freshRuns() {
        Map<String, Run> runs = new LinkedHashMap<>();
        for (Racer racer : RACERS) {
            runs.put(racer.name(), new Run());
        }
        return runs;
    }

    private static boolean everyRacerFinished(Map<String, Run> runs, MapDefinition map) {
        return runs.values().stream().allMatch(run -> run.progress.passedCount() == map.rings().size());
    }

    private static void flyOneTick(MapDefinition map, int mapIndex, int gameTick, Map<String, Run> runs) {
        for (Racer racer : RACERS) {
            Run run = runs.get(racer.name());
            Vec3 position = racer.positionOn(map, mapIndex, gameTick);
            boolean gliding = racer.glidingWith(mapIndex, run.progress.passedCount());

            // run.previous is null on the first tick of the phase — the tick after a teleport has no
            // segment to test. No ring sits within one tick of any spawn, so nothing is lost by it.
            ProgressUpdate update =
                    ProgressTracker.advance(run.progress, map.rings(), run.previous, position, gliding);

            run.previous = position;
            run.progress = update.progress();

            Ring passed = update.passed();
            if (passed == null) {
                continue;
            }
            run.passedOnGameTick.add(gameTick);
            Optional<RingEffect> effect = EFFECTS.effectFor(passed.type());
            if (effect.isPresent()) {
                run.velocity = effect.get().apply(run.velocity);
                run.effectsApplied++;
            }
            if (run.progress.passedCount() == map.rings().size()) {
                run.finishedOnGameTick = gameTick;
            }
        }
    }

    private static void scoreTheMap(int mapIndex, MapDefinition map, GameMode mode, Map<String, Run> runs) {
        // Each score goes in tagged with the racer it belongs to and comes back the same way, so
        // nothing here is index-aligned by hand with RACERS.
        List<Placement<String>> beforePlacement = new ArrayList<>(RACERS.size());
        for (Racer racer : RACERS) {
            Run run = runs.get(racer.name());
            Duration elapsed = run.finishedOnGameTick == NOT_FINISHED
                    ? TICK.multipliedBy(GAME_TICKS)
                    : TICK.multipliedBy(run.finishedOnGameTick);
            beforePlacement.add(new Placement<>(racer.name(), MapScorer.score(run.progress, map, elapsed)));
        }

        for (Placement<String> awarded : PlacementBonus.award(beforePlacement, mode)) {
            Run run = runs.get(awarded.key());
            RESULTS.add(new Result(mapIndex, map.name(), awarded.key(), run.progress,
                    List.copyOf(run.passedOnGameTick), run.finishedOnGameTick, awarded.score(), run.velocity,
                    run.effectsApplied));
        }
    }

    private static Map<String, CupScore> accumulateCupScores() {
        Map<String, CupScore> byRacer = new LinkedHashMap<>();
        for (Racer racer : RACERS) {
            List<MapScore> perMap = RESULTS.stream()
                    .filter(result -> result.racer().equals(racer.name()))
                    .map(Result::score)
                    .toList();
            byRacer.put(racer.name(), CupScorer.accumulate(perMap));
        }
        return byRacer;
    }

    private static Result resultFor(int mapIndex, String racer) {
        return RESULTS.stream()
                .filter(result -> result.mapIndex() == mapIndex && result.racer().equals(racer))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no result for %s on map %d".formatted(racer, mapIndex)));
    }

    // ------------------------------------------------------------------------------------------
    // The gate
    // ------------------------------------------------------------------------------------------

    /**
     * The spec's E3 criterion, stated as an assertion: every map in the cup actually reaches
     * {@link RacePhase#GAME} and actually produces a scored result.
     *
     * <p><strong>This is the assertion the old tree would fail, and it is redundant with nothing else
     * in this class. Do not remove it.</strong> There, {@code GamePhaseFactory} builds a linear
     * three-phase series — lobby, game, end — with no edge back into game. Its {@code end} phase's
     * finish callback loads map two's data and announces it, and then the linear series advances past
     * {@code end} anyway. Map two is therefore loaded, named in chat, and never raced. An assertion
     * that map two's definition resolved would have passed there; only an assertion that map two
     * reached {@code GAME} and produced results catches it, which is why both halves are checked
     * below.
     */
    @Test
    void everyMapInTheCupIsActuallyPlayed() {
        assertThat(MAP_INDICES_THAT_REACHED_GAME)
                .as("map indices that reached the GAME phase")
                .containsExactly(0, 1);

        assertThat(RESULTS).extracting(Result::mapName)
                .containsExactly("ember-ascent", "ember-ascent", "ember-ascent",
                        "glacier-chicane", "glacier-chicane", "glacier-chicane");

        // Not just loaded: raced. Map two's rings were passed, on map two's own ring count of four.
        assertThat(resultFor(1, "Rook").progress().passedCount()).isEqualTo(4);
        assertThat(resultFor(1, "Pike").progress().passedCount()).isEqualTo(4);
    }

    @Test
    void theCupRunsToItsOwnEnd() {
        // 40 lobby + 160 game + 20 end ticks per map, twice over.
        assertThat(TICK.multipliedBy(LOBBY_TICKS)).isEqualTo(TIMINGS.lobby());
        assertThat(TICK.multipliedBy(GAME_TICKS)).isEqualTo(TIMINGS.race());
        assertThat(TICK.multipliedBy(END_TICKS)).isEqualTo(TIMINGS.end());
        assertThat(ticksSimulated).isEqualTo(EXPECTED_TICKS);
        assertThat(EXPECTED_TICKS).isEqualTo(2 * (LOBBY_TICKS + GAME_TICKS + END_TICKS));

        assertThat(finalState.cupFinished()).isTrue();
        assertThat(finalState.phase()).isEqualTo(RacePhase.END);
        assertThat(finalState.mapIndex()).isEqualTo(1);

        // Nobody ever completed a map together with everyone else, so every GAME phase ended on its
        // time limit rather than early.
        assertThat(everyRacerFinishedWasSignalled).isFalse();
    }

    @Test
    void aRacerWhoFliesEveryRingFinishesAndOneWhoStopsHalfwayDoesNotButKeepsRingPoints() {
        // Rook flies both courses whole.
        assertThat(resultFor(0, "Rook").progress().passedCount()).isEqualTo(5);
        assertThat(resultFor(0, "Rook").score().medal()).isEqualTo(MedalTier.DIAMOND);
        assertThat(resultFor(1, "Rook").progress().passedCount()).isEqualTo(4);
        assertThat(resultFor(1, "Rook").score().medal()).isEqualTo(MedalTier.DIAMOND);

        // Pike closes his elytra after two of map one's five rings: DNF, no medal points, and the
        // eighteen points for the two rings he did pass are still his.
        Result pikeOnMapOne = resultFor(0, "Pike");
        assertThat(pikeOnMapOne.progress().passedCount()).isEqualTo(2);
        assertThat(pikeOnMapOne.finishedOnGameTick()).isEqualTo(NOT_FINISHED);
        assertThat(pikeOnMapOne.score().medal()).isEqualTo(MedalTier.DNF);
        assertThat(pikeOnMapOne.score().medalPoints()).isZero();
        assertThat(pikeOnMapOne.score().ringPoints()).as("rings 0 and 1 of ember-ascent, 7 + 11").isEqualTo(18);

        // Wren does the same on map two after one of four rings — a different ring count on a
        // different map, so neither number could be hardcoded.
        Result wrenOnMapTwo = resultFor(1, "Wren");
        assertThat(wrenOnMapTwo.progress().passedCount()).isEqualTo(1);
        assertThat(wrenOnMapTwo.finishedOnGameTick()).isEqualTo(NOT_FINISHED);
        assertThat(wrenOnMapTwo.score().medal()).isEqualTo(MedalTier.DNF);
        assertThat(wrenOnMapTwo.score().ringPoints()).as("ring 0 of glacier-chicane").isEqualTo(17);
    }

    /**
     * Both non-finishers kept flying forward through every remaining ring; the only thing that stopped
     * their progression was the gliding flag. Without that flag Pike would have taken map one's rings
     * 2, 3 and 4 on game ticks 46, 61 and 76, and Wren map two's rings 1, 2 and 3 on ticks 49, 80 and
     * 103 — all inside the 160-tick phase. This checks the crossing that the tracker declined,
     * directly, so that "he did not pass it" cannot quietly become "he never got near it".
     */
    @Test
    void theGlidingFlagIsWhatStopsProgressionNotTheGeometry() {
        Ring emberRingTwo = EMBER_ASCENT.rings().get(2);
        assertThat(RingPass.crosses(PIKE.positionOn(EMBER_ASCENT, 0, 45), PIKE.positionOn(EMBER_ASCENT, 0, 46),
                emberRingTwo)).isTrue();
        assertThat(resultFor(0, "Pike").progress().passedCount()).isEqualTo(2);

        Ring glacierRingOne = GLACIER_CHICANE.rings().get(1);
        assertThat(RingPass.crosses(WREN.positionOn(GLACIER_CHICANE, 1, 48), WREN.positionOn(GLACIER_CHICANE, 1, 49),
                glacierRingOne)).isTrue();
        assertThat(resultFor(1, "Wren").progress().passedCount()).isEqualTo(1);
    }

    /**
     * Every ring, on the tick it was actually passed, for every racer on both maps.
     *
     * <p>This is the assertion that makes the tilted normals load-bearing. The crossing point of a lane
     * with a ring's plane is {@code ring.cz} plus the lateral term from the class comment, and only a
     * tilted normal gives that term a value at all. If {@link RingPass} ignored a ring's stored normal
     * and assumed {@code (0, 0, 1)} — the shape the old tree's shipped maps never exercised — then
     * every crossing would sit on the ring's own {@code z} and nine of the ticks below would move:
     * Rook's map-one rings 2 and 3 to 36 and 46; Wren's map-one rings 0, 1, 2 and 3 to 15, 30, 45 and
     * 58; Rook's map-two ring 1 to 25; Wren's map-two ring 0 to 21; and Pike's map-two ring 2 to 79.
     * The finish ticks alone would not have noticed, because the lateral offsets there are smaller
     * than one tick of travel.
     *
     * <p>Do not trim this test down to the finish ticks on the grounds that the normals are "only
     * fixture variety". They are varied precisely so this assertion can depend on them, and it is the
     * only assertion that does.
     *
     * <pre>
     * ember-ascent, spawn z 0.0        crossing z per lane                             tick
     *   Rook  2.65 b/t   29.8750  62.7500   90.5000  122.7333  152.4500   12 24 35 47 58
     *   Wren  2.10 b/t   32.5000  64.2500   95.0000  127.6667  152.0750   16 31 46 61 73
     *   Pike  2.00 b/t   30.4375  61.6250   (elytra closed)                16 31
     *
     * glacier-chicane, spawn z 3.0
     *   Rook  4.40 b/t   48.3750 107.2500  169.3333  222.2500              11 24 38 50
     *   Wren  2.15 b/t   45.7500  (elytra closed)                          20
     *   Pike  2.11 b/t   47.8125 109.1250  170.3333  223.4875              22 51 80 105
     * </pre>
     */
    @Test
    void everyRingIsPassedOnTheTickTheLaneGeometrySays() {
        assertThat(resultFor(0, "Rook").passedOnGameTick()).containsExactly(12, 24, 35, 47, 58);
        assertThat(resultFor(0, "Wren").passedOnGameTick()).containsExactly(16, 31, 46, 61, 73);
        assertThat(resultFor(0, "Pike").passedOnGameTick()).containsExactly(16, 31);

        assertThat(resultFor(1, "Rook").passedOnGameTick()).containsExactly(11, 24, 38, 50);
        assertThat(resultFor(1, "Wren").passedOnGameTick()).containsExactly(20);
        assertThat(resultFor(1, "Pike").passedOnGameTick()).containsExactly(22, 51, 80, 105);
    }

    /**
     * The elapsed time a finisher is scored on is the tick they passed the final ring, not the tick the
     * phase ended. Every GAME phase here ran its full 160 ticks — 8.000 s — and not one recorded time
     * is 8.000 s.
     */
    @Test
    void aFinishersTimeIsTheTickTheyPassedTheFinalRingNotTheEndOfThePhase() {
        // Hand-derived from the crossing formula in the class comment. Map one, ring 4, centre
        // (-1.5, 63.5, 152.0), normal (-0.36, 0.48, 0.8):
        //   Rook's lane (1.5, 65.0): crossing z = 152.0 + (-0.36 * -3.0 + 0.48 * -1.5) / 0.8 = 152.450
        //                            tick = ceil(152.450 / 2.65) = ceil(57.528) = 58 -> 2.900 s
        //   Wren's lane (-2.0, 63.0): crossing z = 152.0 + (-0.36 * 0.5 + 0.48 * 0.5) / 0.8 = 152.075
        //                            tick = ceil(152.075 / 2.10) = ceil(72.417) = 73 -> 3.650 s
        assertThat(resultFor(0, "Rook").finishedOnGameTick()).isEqualTo(58);
        assertThat(resultFor(0, "Rook").score().completionTime()).contains(Duration.ofMillis(2_900));
        assertThat(resultFor(0, "Wren").finishedOnGameTick()).isEqualTo(73);
        assertThat(resultFor(0, "Wren").score().completionTime()).contains(Duration.ofMillis(3_650));

        // Map two, ring 3, centre (0.5, 65.5, 223.0), normal (0.36, -0.48, 0.8), spawn z 3.0:
        //   Rook: crossing z = 223.0 + (0.36 * -1.0 + -0.48 * 0.5) / 0.8 = 222.250
        //         tick = ceil((222.250 - 3.0) / 4.40) = ceil(49.830) = 50 -> 2.500 s
        //   Pike's lane (0.75, 66.5): crossing z = 223.0 + (0.36 * -0.25 + -0.48 * -1.0) / 0.8 = 223.4875
        //         tick = ceil((223.4875 - 3.0) / 2.11) = ceil(104.497) = 105 -> 5.250 s
        assertThat(resultFor(1, "Rook").finishedOnGameTick()).isEqualTo(50);
        assertThat(resultFor(1, "Rook").score().completionTime()).contains(Duration.ofMillis(2_500));
        assertThat(resultFor(1, "Pike").finishedOnGameTick()).isEqualTo(105);
        assertThat(resultFor(1, "Pike").score().completionTime()).contains(Duration.ofMillis(5_250));

        Duration phaseEnd = TICK.multipliedBy(GAME_TICKS);
        assertThat(phaseEnd).isEqualTo(Duration.ofSeconds(8));
        assertThat(RESULTS).filteredOn(result -> result.score().medal() != MedalTier.DNF)
                .extracting(result -> result.score().completionTime().orElseThrow())
                .doesNotContain(phaseEnd)
                .containsExactly(Duration.ofMillis(2_900), Duration.ofMillis(3_650),
                        Duration.ofMillis(2_500), Duration.ofMillis(5_250));
    }

    /** Medals are classified against each map's own reference time, not one shared number. */
    @Test
    void medalsFollowEachMapsOwnReferenceTime() {
        // Map one, reference 3.000 s: DIAMOND <= 3.000, GOLD <= 3.300, SILVER <= 3.750, BRONZE <= 4.500.
        //   Rook 2.900 s -> DIAMOND.  Wren 3.650 s -> SILVER (past gold at 3.300, inside silver at 3.750).
        assertThat(EMBER_ASCENT.referenceTime()).isEqualTo(Duration.ofSeconds(3));
        assertThat(resultFor(0, "Rook").score().medal()).isEqualTo(MedalTier.DIAMOND);
        assertThat(resultFor(0, "Wren").score().medal()).isEqualTo(MedalTier.SILVER);

        // Map two, reference 5.000 s: DIAMOND <= 5.000, GOLD <= 5.500, SILVER <= 6.250.
        //   Rook 2.500 s -> DIAMOND.  Pike 5.250 s -> GOLD. Against map one's 3 s reference Pike's
        //   5.250 s would have been a FINISH, so this pins the per-map reference and not just the
        //   bracket table.
        assertThat(GLACIER_CHICANE.referenceTime()).isEqualTo(Duration.ofSeconds(5));
        assertThat(resultFor(1, "Rook").score().medal()).isEqualTo(MedalTier.DIAMOND);
        assertThat(resultFor(1, "Pike").score().medal()).isEqualTo(MedalTier.GOLD);
    }

    /**
     * Placement is recomputed per map from that map's own totals. Wren is second on map one and third
     * on map two; Pike is third and then second. A bonus carried over from the previous map, or one
     * derived from the cup standing rather than the map standing, would put them in the same order
     * twice.
     */
    @Test
    void placementIsAwardedPerMapFromThatMapsOwnTotals() {
        // Map one totals before placement: Rook 113 + 60 = 173, Wren 113 + 30 = 143, Pike 18 + 0 = 18.
        assertThat(resultFor(0, "Rook").score().placementBonus()).isEqualTo(10);
        assertThat(resultFor(0, "Wren").score().placementBonus()).isEqualTo(6);
        assertThat(resultFor(0, "Pike").score().placementBonus()).isEqualTo(3);

        // Map two totals before placement: Rook 114 + 60 = 174, Pike 114 + 45 = 159, Wren 17 + 0 = 17.
        assertThat(resultFor(1, "Rook").score().placementBonus()).isEqualTo(10);
        assertThat(resultFor(1, "Pike").score().placementBonus()).isEqualTo(6);
        assertThat(resultFor(1, "Wren").score().placementBonus()).isEqualTo(3);
    }

    /**
     * The cup standing, with every number written out.
     *
     * <p>Ring points are each passed ring's own value; medal points are 60 / 45 / 30 / 15 / 5 / 0 for
     * diamond / gold / silver / bronze / finish / DNF; placement is 10 / 6 / 3 and then 1 flat.
     *
     * <pre>
     * Map "ember-ascent" — 5 rings worth 7, 11, 21, 33, 41 — 3.000 s reference
     *                            ring pts        time     medal        placement    map total
     *   Rook   7+11+21+33+41 =        113     2.900 s     DIAMOND  60   1st  +10  =        183
     *   Wren   7+11+21+33+41 =        113     3.650 s     SILVER   30   2nd   +6  =        149
     *   Pike   7+11          =         18     did not finish  DNF   0   3rd   +3  =         21
     *
     * Map "glacier-chicane" — 4 rings worth 17, 23, 31, 43 — 5.000 s reference
     *   Rook   17+23+31+43   =        114     2.500 s     DIAMOND  60   1st  +10  =        184
     *   Pike   17+23+31+43   =        114     5.250 s     GOLD     45   2nd   +6  =        165
     *   Wren   17            =         17     did not finish  DNF   0   3rd   +3  =         20
     *
     * Cup totals
     *   Rook   183 + 184 = 367
     *   Pike    21 + 165 = 186
     *   Wren   149 +  20 = 169
     * </pre>
     *
     * <p>Rook wins the cup, Pike comes second having lost map one badly and won a medal on map two,
     * and Wren comes third having done the reverse. The ordering is not the map-one ordering, so a
     * cup total that were really just the first map's standing would be visible.
     */
    @Test
    void cupTotalsAreOrderedAsThePlayWarrants() {
        assertThat(resultFor(0, "Rook").score().total()).isEqualTo(183);
        assertThat(resultFor(0, "Wren").score().total()).isEqualTo(149);
        assertThat(resultFor(0, "Pike").score().total()).isEqualTo(21);
        assertThat(resultFor(1, "Rook").score().total()).isEqualTo(184);
        assertThat(resultFor(1, "Pike").score().total()).isEqualTo(165);
        assertThat(resultFor(1, "Wren").score().total()).isEqualTo(20);

        assertThat(cupScores.get("Rook").totalPoints()).isEqualTo(367);
        assertThat(cupScores.get("Pike").totalPoints()).isEqualTo(186);
        assertThat(cupScores.get("Wren").totalPoints()).isEqualTo(169);

        assertThat(cupScores.get("Rook").totalPoints())
                .isGreaterThan(cupScores.get("Pike").totalPoints());
        assertThat(cupScores.get("Pike").totalPoints())
                .isGreaterThan(cupScores.get("Wren").totalPoints());
    }

    /**
     * A map a racer did not finish contributes its ring points but neither a time nor a finished-map
     * count. Rook's best time comes from map two, Wren's from map one — so a best time that were
     * always the first map's could not pass — and Pike's from the only map he finished.
     */
    @Test
    void theCupBestTimeCountsOnlyFinishedMaps() {
        assertThat(cupScores.get("Rook").mapsFinished()).isEqualTo(2);
        assertThat(cupScores.get("Rook").bestTime()).contains(Duration.ofMillis(2_500));

        assertThat(cupScores.get("Wren").mapsFinished()).isEqualTo(1);
        assertThat(cupScores.get("Wren").bestTime()).contains(Duration.ofMillis(3_650));

        assertThat(cupScores.get("Pike").mapsFinished()).isEqualTo(1);
        assertThat(cupScores.get("Pike").bestTime()).contains(Duration.ofMillis(5_250));

        // The DNF rows have no completion time at all. They were handed the full phase duration as
        // their elapsed time — 8.000 s, which would have been the smallest value in Pike's cup and
        // the largest in Wren's — and the scorer declined to call it a result.
        assertThat(resultFor(0, "Pike").score().completionTime()).isEmpty();
        assertThat(resultFor(1, "Wren").score().completionTime()).isEmpty();
        assertThat(cupScores.values()).extracting(CupScore::bestTime)
                .doesNotContain(Optional.of(Duration.ofSeconds(8)));
    }

    /** Checkpoints are recorded as they are passed, and only by the racers who reached them. */
    @Test
    void checkpointsAreRecordedWhenTheyArePassed() {
        // Map one's only CHECKPOINT is ring 2; Pike stopped after ring 1 and so reached none.
        assertThat(resultFor(0, "Rook").progress().lastCheckpointIndex()).isEqualTo(2);
        assertThat(resultFor(0, "Wren").progress().lastCheckpointIndex()).isEqualTo(2);
        assertThat(resultFor(0, "Pike").progress().lastCheckpointIndex()).isEqualTo(-1);

        // Map two's CHECKPOINT is ring 0, so even Wren, who stopped after it, has it recorded.
        assertThat(resultFor(1, "Rook").progress().lastCheckpointIndex()).isZero();
        assertThat(resultFor(1, "Pike").progress().lastCheckpointIndex()).isZero();
        assertThat(resultFor(1, "Wren").progress().lastCheckpointIndex()).isZero();
    }

    /**
     * The effect a ring leaves behind is decided by its type as the course is flown. Map one has two
     * BOOST rings (1 and 4), map two one SLOW ring (1); STANDARD and CHECKPOINT rings leave nothing.
     *
     * <p>4.0 x 1.5 x 1.5 = 9.0 for a racer who took both boosts, 4.0 x 1.5 = 6.0 for Pike, who stopped
     * before the second, and 4.0 x 0.5 = 2.0 on map two. Wren, who stopped on map two after its
     * CHECKPOINT ring, is untouched at 4.0 — the case that separates "no effect registered" from "the
     * effect was never looked up".
     */
    @Test
    void ringEffectsAreDecidedByRingTypeAlongTheWay() {
        assertThat(resultFor(0, "Rook").effectsApplied()).isEqualTo(2);
        assertThat(resultFor(0, "Rook").velocity()).isEqualTo(new Vec3(0.0, 0.0, 9.0));
        assertThat(resultFor(0, "Wren").effectsApplied()).isEqualTo(2);
        assertThat(resultFor(0, "Wren").velocity()).isEqualTo(new Vec3(0.0, 0.0, 9.0));
        assertThat(resultFor(0, "Pike").effectsApplied()).isEqualTo(1);
        assertThat(resultFor(0, "Pike").velocity()).isEqualTo(new Vec3(0.0, 0.0, 6.0));

        assertThat(resultFor(1, "Rook").effectsApplied()).isEqualTo(1);
        assertThat(resultFor(1, "Rook").velocity()).isEqualTo(new Vec3(0.0, 0.0, 2.0));
        assertThat(resultFor(1, "Pike").effectsApplied()).isEqualTo(1);
        assertThat(resultFor(1, "Pike").velocity()).isEqualTo(new Vec3(0.0, 0.0, 2.0));
        assertThat(resultFor(1, "Wren").effectsApplied()).isZero();
        assertThat(resultFor(1, "Wren").velocity()).isEqualTo(NOMINAL_VELOCITY);
    }

    // ------------------------------------------------------------------------------------------
    // Harness types
    // ------------------------------------------------------------------------------------------

    /**
     * A simulated racer: a fixed lane, a speed per map, and the ring count after which the elytra
     * closes on each map. Positions are a closed-form function of the game tick, so no assertion in
     * this class depends on a value the production code computed.
     */
    private record Racer(String name, double laneX, double laneY, List<Double> blocksPerTickByMap,
            List<Integer> glidesUntilRingCountByMap) {

        Vec3 positionOn(MapDefinition map, int mapIndex, int gameTick) {
            double travelled = blocksPerTickByMap.get(mapIndex) * gameTick;
            return new Vec3(laneX, laneY, map.spawn().z() + travelled);
        }

        boolean glidingWith(int mapIndex, int passedCount) {
            return passedCount < glidesUntilRingCountByMap.get(mapIndex);
        }
    }

    /** One racer's mutable state over one map. */
    private static final class Run {

        private final List<Integer> passedOnGameTick = new ArrayList<>();
        private RingProgress progress = RingProgress.atStart();
        private Vec3 previous;
        private int finishedOnGameTick = NOT_FINISHED;
        private Vec3 velocity = NOMINAL_VELOCITY;
        private int effectsApplied;
    }

    /** What one racer's run on one map came to, frozen for the assertions to read. */
    private record Result(int mapIndex, String mapName, String racer, RingProgress progress,
            List<Integer> passedOnGameTick, int finishedOnGameTick, MapScore score, Vec3 velocity,
            int effectsApplied) {
    }
}
