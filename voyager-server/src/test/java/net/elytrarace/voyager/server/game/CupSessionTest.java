package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.MedalTier;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.platform.tick.FlightTick;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.race.cup.CupStandings;
import net.elytrarace.voyager.race.scoring.MapScore;
import net.kyori.adventure.text.Component;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.projectile.FireworkRocketMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.ActionBarPacket;
import net.minestom.server.network.packet.server.play.BossBarPacket;
import net.minestom.server.network.packet.server.play.SetTitleSubTitlePacket;
import net.minestom.server.network.packet.server.play.SetTitleTextPacket;
import net.minestom.server.network.packet.server.play.SetTitleTimePacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A whole two-map cup, played on a live server, through the same {@link CupSession} the composition
 * root builds.
 *
 * <p>This is the test that answers "does any of it actually compose". Everything below it has been
 * proven in isolation for four tasks — the physics against real Vanilla, the race core in a plain
 * JUnit loop, the world loader against real region files, the transition against two worlds — and
 * none of that says the pieces fit. Here the phase driver, the flight driver, the map transition, the
 * run board and the scorers run together, against worlds read off disk through Falco, with a player
 * connected.
 *
 * <h2>What the fixture is built to tell apart</h2>
 *
 * <p><strong>Two worlds, not one.</strong> Map two's world carries a different block under its own
 * spawn, so "the racer moved to the next map" is asserted against the ground they are standing on —
 * a transition that landed them at the right coordinates in the wrong world would still satisfy a
 * position assertion.
 *
 * <p><strong>The two maps are not interchangeable.</strong> Different ring counts (1 and 2),
 * different ring points (7 and 13), different reference times, different spawn chunks, and map two's
 * spawn is negative on two axes — above zero a chunk index computed by a shift and one computed by a
 * truncating divide agree.
 *
 * <p><strong>Map one is finished and map two is not.</strong> One racer, two maps, two different
 * outcomes: a medal and a {@code DNF}. A session that scored every map the same way, or that carried
 * map one's run into map two, cannot produce both.
 *
 * <p><strong>Ring points are 7 and 13, never 10.</strong> The committed data awards 10 to every ring,
 * so a scorer reading a flat rate rather than the ring's own value passes against it. Not here.
 */
@EnvTest
class CupSessionTest {

    private static final String RIDGE = "ridge_arena";
    private static final String DUNE = "dune_arena";

    private static final Vec3 RIDGE_SPAWN = new Vec3(3.5, 65.0, 5.5);
    private static final Vec3 DUNE_SPAWN = new Vec3(-37.5, 71.0, -88.5);

    private static final Block RIDGE_FLOOR = Block.GOLD_BLOCK;
    private static final Block DUNE_FLOOR = Block.DIAMOND_BLOCK;

    /**
     * Map one's boost tuning: a 4-tick burn and a 9-tick cooldown.
     *
     * <p>Short enough that a whole burn and the cooldown after it fit inside a 20-tick race phase,
     * and small enough that the count of boosted ticks can be asserted exactly rather than
     * approximately. 4 and 9 are not multiples of one another and neither is the Vanilla-derived
     * default of 30, so a session reading the wrong field, or falling back to a built-in, produces a
     * number that appears nowhere in this file.
     */
    private static final BoostConfig RIDGE_BOOST = new BoostConfig(4, 9);

    /**
     * Map two's, and deliberately different on both numbers: a boost read from map one's tuning while
     * map two is being raced would burn for 4 rather than 6.
     */
    private static final BoostConfig DUNE_BOOST = new BoostConfig(6, 13);

    /**
     * One ring, 10 blocks down +z of the spawn and 8 blocks <em>above</em> it, worth 7.
     *
     * <p>The 8 is load-bearing and not decoration: the launch aims its horizontal impulse along the
     * ground line to the first ring, and with a ring level with the spawn that vector and the vector
     * straight at the ring are the same value — a launch that fired the whole impulse at the ring
     * could not be told from one that did not.
     */
    private static final MapDefinition RIDGE_RUN = new MapDefinition("ridge-run", RIDGE, RIDGE_SPAWN,
            List.of(ring(0, RIDGE_SPAWN.plus(new Vec3(0, 8, 10)), 7)),
            Duration.ofMillis(400), RIDGE_BOOST, new GuideLine(List.of(), 2, 1.0));

    /** Two rings, so a run that passed one of them has still not finished. Worth 13 each. */
    private static final MapDefinition DUNE_RUN = new MapDefinition("dune-run", DUNE, DUNE_SPAWN,
            List.of(ring(0, DUNE_SPAWN.plus(new Vec3(0, 0, 10)), 13),
                    ring(1, DUNE_SPAWN.plus(new Vec3(0, 0, 20)), 13)),
            Duration.ofMillis(650), DUNE_BOOST, new GuideLine(List.of(), 4, 1.5));

    private static final CupDefinition CUP =
            new CupDefinition("grand_tour", List.of("ridge-run", "dune-run"), GameMode.RACE);

    /** The catalogue the fixture plays: both maps, and the one cup that plays them in order. */
    private static CatalogHolder holder() {
        return new CatalogHolder(new LoadedCatalog(
                new CatalogSnapshot(Map.of("ridge-run", RIDGE_RUN, "dune-run", DUNE_RUN), Map.of(CUP.name(), CUP)),
                CUP, Instant.EPOCH));
    }

    private static final Duration STEP = Duration.ofMillis(50);

    /**
     * Lobby 2 ticks, race 20 ticks, results 2 ticks. The race length is what a {@code DNF} is scored
     * on, so it has to be long enough that map two's phase genuinely runs out rather than being ended
     * by anything else.
     */
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(100));

    @TempDir
    Path tempDir;

    private final List<Player> racers = new ArrayList<>();
    private MapInstances worlds;

    @AfterEach
    void releaseTheWorlds() {
        // The racers go first: InstanceManager refuses to unregister an instance that still has a
        // player in it, so a test that closed its worlds with somebody standing in one would fail on
        // the close rather than on what it was asserting.
        racers.forEach(racer -> racer.remove(true));
        racers.clear();
        if (worlds != null) {
            worlds.close();
            worlds = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // The whole cup
    // ------------------------------------------------------------------------------------------

    @Test
    void playsAWholeCupFromTheLobbyOfTheFirstMapToTheResultsOfTheLast(Env env) throws IOException {
        Fixture fixture = start(env);

        // Map one: cross the ring on the third game tick, by moving from in front of it to behind it.
        // The first game tick of a run has no previous position and cannot cross anything, which is
        // why the move is not on it.
        fixture.run(tick -> {
            if (fixture.session.describe().contains("ridge-run") && tick == 5) {
                // Crosses the ring's plane at z + 10 and y + 4, four blocks under a centre with a
                // radius of six — inside it, and not through the middle of it.
                fixture.teleport(RIDGE_SPAWN.plus(new Vec3(0, 8, 20)));
            }
        });

        assertThat(fixture.finished()).isTrue();

        MapScore ridge = fixture.scoreOn(0);
        assertThat(ridge.ringPoints())
                .describedAs("one ring worth 7, not a flat rate")
                .isEqualTo(7);
        assertThat(ridge.medal()).isNotEqualTo(MedalTier.DNF);
        assertThat(ridge.completionTime()).isPresent();

        MapScore dune = fixture.scoreOn(1);
        assertThat(dune.ringPoints())
                .describedAs("the second map's two rings were never crossed")
                .isZero();
        assertThat(dune.medal()).isEqualTo(MedalTier.DNF);
        assertThat(dune.completionTime()).isEmpty();

        // The one racer is first on both maps, so both carry the first-place bonus.
        assertThat(ridge.placementBonus()).isEqualTo(10);
        assertThat(dune.placementBonus()).isEqualTo(10);
    }

    /**
     * The advance this whole rebuild exists to make possible, asserted against the ground: the racer
     * ends the cup standing on the second world's block, not the first world's, and in the instance
     * {@code MapInstances} caches that world under.
     */
    @Test
    void movesTheRacerIntoTheSecondMapsOwnWorldWhenTheCupAdvances(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.run(tick -> { });

        assertThat(fixture.racer.getInstance()).isSameAs(fixture.worlds.forWorld(DUNE));
        assertThat(blockUnder(fixture.racer)).isEqualTo(DUNE_FLOOR);
        assertThat(fixture.racer.getPosition().samePoint(spawnOf(DUNE_RUN))).isTrue();
    }

    /**
     * The problem the launch exists to solve: {@code isFlyingWithElytra()} is what every ring pass is
     * gated on, and a racer who joins with nothing and never jumps produces a race in which nothing
     * happens. Asserted on the first tick of the first map, before anything else could have set it.
     */
    @Test
    void putsEveryRacerInTheAirWithAnElytraWhenAMapStarts(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));

        assertThat(fixture.racer.getEquipment(EquipmentSlot.CHESTPLATE).material()).isEqualTo(Material.ELYTRA);
        // And rockets that carry their flight duration. A plain stack would look identical in an
        // inventory and claim a one-gunpowder rocket in its tooltip, against a server that burns the
        // three-gunpowder length — a discrepancy a player notices and cannot explain.
        ItemStack rockets = fixture.racer.getInventory().getItemStack(0);
        assertThat(rockets.material()).isEqualTo(Material.FIREWORK_ROCKET);
        assertThat(rockets.get(DataComponents.FIREWORKS))
                .describedAs("the rocket declares a flight duration")
                .isNotNull()
                .satisfies(fireworks -> assertThat(fireworks.flightDuration()).isEqualTo(3));
        assertThat(fixture.racer.isFlyingWithElytra())
                .describedAs("the server starts the glide; a racer who has to know the trick never races")
                .isTrue();
        assertThat(fixture.racer.getPosition().samePoint(spawnOf(RIDGE_RUN))).isTrue();
        assertThat(fixture.runs.of(fixture.racer.getUuid())).isPresent();

        // The impulse, read back in the blocks-per-second Minestom carries. The ring is 8 above the
        // spawn, so a launch that aimed the whole impulse at it would tilt both the z and the y
        // component away from these two values.
        Vec launch = fixture.racer.getVelocity();
        assertThat(launch.x()).isZero();
        assertThat(launch.z()).isEqualTo(Racers.LAUNCH_FORWARD_PER_TICK * ServerFlag.SERVER_TICKS_PER_SECOND);
        assertThat(launch.y()).isEqualTo(Racers.LAUNCH_UP_PER_TICK * ServerFlag.SERVER_TICKS_PER_SECOND);
    }

    /**
     * A map ends and the racer stops gliding. Without it a racer sits in the results screen still in
     * flight, carrying the velocity they finished on, and the next map's launch is applied on top of
     * whatever that had become — which makes every map after the first start differently.
     */
    @Test
    void standsTheRacerDownWhenAMapEnds(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.runUntil(tick -> fixture.session.describe().contains("phase END"));

        assertThat(fixture.racer.isFlyingWithElytra()).isFalse();
        assertThat(fixture.racer.getVelocity()).isEqualTo(Vec.ZERO);
    }

    /**
     * The flight simulation is pointed at the world being raced. Nothing else would notice if it were
     * not: with no world attached every block read answers "unknown", the collision space finds
     * nothing, and a racer flying through solid rock looks exactly like one flying through open sky.
     */
    @Test
    void pointsTheFlightSimulationAtTheWorldBeingRaced(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));

        assertThat(fixture.session.describe()).contains("collision world: attached");
    }

    /**
     * The server's own flight simulation runs alongside the client's, which is the whole reason
     * {@code FlightTickDriver} is on the tick at all. Nothing else in this class would notice if it
     * were never called: ring collision reads the client's position, so the simulation is silent, and
     * a silent thing that is not running looks exactly like one that is.
     *
     * <p>Asserted as "a simulated position exists and it is not the client's". They cannot agree here
     * — the racer is teleported and the simulation is not — which is what makes the reading a reading
     * rather than an echo.
     */
    @Test
    void runsTheServersOwnFlightSimulationAlongsideTheClientsPosition(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.runUntil(tick -> {
            if (tick == 5) {
                fixture.teleport(RIDGE_SPAWN.plus(new Vec3(0, 40, 0)));
            }
            return tick >= 6;
        });

        String status = fixture.session.describe();
        assertThat(status).contains("simulated");
        assertThat(status).doesNotContain("not simulated");
    }

    /**
     * A ring crossed by somebody who is not gliding does not count. The flag the run is advanced with
     * has to be read off the racer rather than assumed — in a race everybody is gliding almost all of
     * the time, which is exactly what makes a hard-coded {@code true} invisible.
     */
    @Test
    void doesNotCountARingCrossedWhileTheRacerIsNotGliding(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.run(tick -> {
            if (fixture.session.describe().contains("ridge-run") && tick == 5) {
                fixture.racer.setFlyingWithElytra(false);
                fixture.teleport(RIDGE_SPAWN.plus(new Vec3(0, 8, 20)));
            }
        });

        assertThat(fixture.scoreOn(0).ringPoints())
                .describedAs("the same movement that scores 7 while gliding scores nothing on foot")
                .isZero();
        assertThat(fixture.scoreOn(0).medal()).isEqualTo(MedalTier.DNF);
    }

    /**
     * The core rule of a race: a map ends when everyone has finished, rather than running out its
     * race length with nothing left happening. Compared against the same cup flown by a racer who
     * crosses nothing — the only difference between the two runs is the finish, so the difference in
     * length is the finish ending the phase.
     */
    @Test
    void endsAMapAsSoonAsEveryRacerHasFinishedRatherThanRunningOutTheRaceLength(Env env) throws IOException {
        Fixture nobodyFinishes = start(env);
        nobodyFinishes.run(tick -> { });
        int fullLength = nobodyFinishes.ticksPlayed;
        nobodyFinishes.release();

        Fixture finisher = start(env);
        finisher.run(tick -> {
            if (finisher.session.describe().contains("ridge-run") && tick == 5) {
                finisher.teleport(RIDGE_SPAWN.plus(new Vec3(0, 8, 20)));
            }
        });

        assertThat(finisher.scoreOn(0).completionTime()).isPresent();
        assertThat(finisher.ticksPlayed)
                .describedAs("finishing map one ends its race phase, so the cup is shorter than one nobody finished")
                .isLessThan(fullLength);
    }

    /**
     * The simulation reasons in blocks per tick. Minestom carries an entity's velocity in blocks per
     * second, and a seed handed over unconverted is twenty times too fast — which produces a
     * self-consistent simulation that is simply wrong, the exact shape of defect the E2a trace work
     * spent two passes on.
     */
    @Test
    void seedsTheSimulationWithAVelocityInBlocksPerTickAndNotBlocksPerSecond(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.runUntil(tick -> tick >= 4);

        FlightTick simulated = fixture.session.lastSimulated(fixture.racer.getUuid()).orElseThrow();
        // The launch is 0.8 forward and 1.2 up per tick, so about 1.44 blocks per tick. The same
        // numbers read as blocks per second would seed about 28.8, and no elytra step brings that
        // under five in three ticks.
        assertThat(simulated.after().velocity().length())
                .describedAs("simulated speed in blocks per tick")
                .isLessThan(5.0);
    }

    /**
     * A racer who disconnects leaves nothing behind. Without it the board holds an entry for somebody
     * who is gone, and {@code RaceRuns.everyRacerFinished()} then waits for a finish that cannot
     * arrive — the map never ends early again.
     */
    @Test
    void forgetsWhatItHeldForARacerWhoDisconnects(Env env) throws IOException {
        Fixture fixture = start(env);
        // Four ticks, not "the first GAME tick": the flight driver samples at the top of a tick and
        // the launch sets the gliding flag later in the same one, so the map's opening tick is the
        // one tick on which nothing has been simulated yet.
        fixture.runUntil(tick -> tick >= 4);
        java.util.UUID id = fixture.racer.getUuid();
        assertThat(fixture.runs.of(id)).isPresent();
        assertThat(fixture.session.lastSimulated(id)).isPresent();

        fixture.session.forget(id);

        assertThat(fixture.runs.of(id)).isEmpty();
        assertThat(fixture.session.lastSimulated(id)).isEmpty();
    }

    /**
     * {@code /race skip} ends the map it is used on and nothing else. Without the requested skip the
     * same cup runs both race phases to their limit, which is what the tick count is compared
     * against — a skip that did nothing would be indistinguishable from one that worked if only the
     * end state were asserted.
     */
    @Test
    void skippingEndsTheCurrentMapEarlyRatherThanRunningItsFullRaceLength(Env env) throws IOException {
        Fixture unskipped = start(env);
        unskipped.run(tick -> { });
        int fullLength = unskipped.ticksPlayed;
        unskipped.release();

        Fixture skipped = start(env);
        skipped.run(tick -> {
            if (tick == 5) {
                assertThat(skipped.session.requestSkip())
                        .describedAs("the first map is racing by tick 5, so a skip is taken")
                        .isTrue();
            }
        });

        assertThat(skipped.ticksPlayed)
                .describedAs("skipping one of two race phases has to cost fewer ticks than playing both out")
                .isLessThan(fullLength);
        assertThat(skipped.finished()).isTrue();
    }

    /**
     * A skip asked for while nothing is racing is refused, not banked. A flag set during the lobby
     * would survive to the first tick of the map that follows and end <em>that</em> map — a landmine
     * armed minutes earlier by somebody who saw no effect at the time.
     *
     * <p>Compared against an untouched run of the same cup rather than against a number: "the same
     * length as if nothing had been asked for" is the claim, and a tick count written into the test
     * would only be asserting the fixture's timings back at itself.
     */
    @Test
    void aSkipAskedForWhileNothingIsRacingIsRefusedRatherThanBankedForTheNextMap(Env env) throws IOException {
        Fixture untouched = start(env);
        untouched.run(tick -> { });
        int fullLength = untouched.ticksPlayed;
        untouched.release();

        Fixture fixture = start(env);
        boolean taken = fixture.session.requestSkip();
        fixture.run(tick -> { });

        assertThat(taken).describedAs("nothing was racing, so there was nothing to skip").isFalse();
        assertThat(fixture.ticksPlayed)
                .describedAs("the cup ran exactly as long as one nobody asked to skip")
                .isEqualTo(fullLength);
        assertThat(fixture.scoreOn(0).medal()).isEqualTo(MedalTier.DNF);
    }

    /** A skip taken during a map a restart abandons must not end the first map of the new cup. */
    @Test
    void restartingTheCupDropsASkipThatWasNeverConsumed(Env env) throws IOException {
        Fixture untouched = start(env);
        untouched.run(tick -> { });
        int fullLength = untouched.ticksPlayed;
        untouched.release();

        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        assertThat(fixture.session.requestSkip()).isTrue();
        fixture.session.start(true);
        int beforeRestart = fixture.ticksPlayed;
        fixture.run(tick -> { });

        assertThat(fixture.ticksPlayed - beforeRestart)
                .describedAs("the restarted cup lost only its lobby, not its first race phase")
                .isGreaterThan(fullLength - 10);
    }

    /**
     * A cup that is restarted does not continue the last one. The racer finishes map one, the cup is
     * restarted, and map one's score is the score of the replay — one row, not two.
     */
    @Test
    void restartingTheCupDropsTheScoresOfTheRunThatWasAbandoned(Env env) throws IOException {
        Fixture fixture = start(env);

        fixture.runUntil(tick -> fixture.session.describe().contains("dune-run"));
        fixture.session.start(true);
        fixture.run(tick -> { });

        assertThat(fixture.standings())
                .describedAs("two maps, each scored once, after a restart part-way through")
                .hasSize(2);
    }

    // ------------------------------------------------------------------------------------------
    // The firework boost
    // ------------------------------------------------------------------------------------------

    /**
     * The measurement the whole boost turns on: a burn configured for 4 ticks reaches the simulation
     * on exactly 4 ticks.
     *
     * <p>Counted by reading {@code FlightTick.input()} — the input the simulator was actually handed
     * — rather than by asking the tracker, which would only assert the tracker against itself. It is
     * also the assertion that catches the ordering inside {@code tick()}: advancing the burn before
     * the sample is taken spends a tick nothing observed, and this count comes back 3.
     */
    @Test
    void aBoostReachesTheSimulationOnExactlyTheMapsOwnNumberOfTicks(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));

        assertThat(fixture.session.requestBoost(fixture.racer))
                .describedAs("the racer is gliding on map one and has boosted nothing yet")
                .isTrue();

        int boostedTicks = fixture.countBoostedTicks(12);

        assertThat(boostedTicks).isEqualTo(RIDGE_BOOST.burnDurationTicks());
    }

    /**
     * The second map's tuning is the one used on the second map. A session that read the first map's
     * boost config, or one it cached when the cup started, burns for 4 here instead of 6.
     */
    @Test
    void theSecondMapsOwnBurnLengthIsUsedOnTheSecondMap(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("dune-run")
                && fixture.session.describe().contains("phase GAME"));

        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();

        assertThat(fixture.countBoostedTicks(15))
                .describedAs("map two burns for %s, map one for %s",
                        DUNE_BOOST.burnDurationTicks(), RIDGE_BOOST.burnDurationTicks())
                .isEqualTo(DUNE_BOOST.burnDurationTicks());
    }

    /**
     * The other half of a boost, and the only half a client can see: a real firework rocket entity,
     * in the world being raced, naming this racer as its shooter. The simulation's side of the boost
     * is silent by design, so without this a session that told the tracker and spawned nothing would
     * pass every other boost test in this class — and a player would press the button and see, and
     * feel, nothing.
     */
    @Test
    void aBoostSpawnsARocketAttachedToTheRacerInTheWorldBeingRaced(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        assertThat(rocketsAround(fixture.racer)).isEmpty();

        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();

        List<Entity> rockets = rocketsAround(fixture.racer);
        assertThat(rockets).hasSize(1);
        assertThat(((FireworkRocketMeta) rockets.getFirst().getEntityMeta()).getShooterEntityId())
                .isEqualTo(fixture.racer.getEntityId());
    }

    /** A refused boost lights nothing: no rocket, no cooldown packet, nothing for a player to see. */
    @Test
    void aRefusedBoostSpawnsNoRocket(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();
        int afterOne = rocketsAround(fixture.racer).size();

        assertThat(fixture.session.requestBoost(fixture.racer)).isFalse();

        assertThat(rocketsAround(fixture.racer))
                .describedAs("the refused second request added no second rocket")
                .hasSize(afterOne);
    }

    /** A rocket used during the cooldown is refused, and the burn already running is not restarted. */
    @Test
    void aSecondRocketDuringTheCooldownIsRefused(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();
        fixture.runUntil(tick -> tick >= 1);

        assertThat(fixture.session.requestBoost(fixture.racer)).isFalse();

        assertThat(fixture.session.describe())
                .describedAs("the refused request left the first burn counting down as it was")
                .contains("boost burning 3 tick(s)");
    }

    /**
     * A boost asked for between maps has no tuning to start a burn under, so it is refused rather
     * than started on some default or on whichever map happened to be current last.
     *
     * <p>The racer is put into a glide by hand first, and that is the point of the test rather than
     * an incidental setup step: with the gliding flag down, the refusal would come from the tracker's
     * own check and this would prove nothing about the map guard. Gliding, the only thing left that
     * can refuse is "no map is being raced".
     */
    @Test
    void aBoostAskedForWhileNoMapIsBeingRacedIsRefused(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.racer.setFlyingWithElytra(true);

        assertThat(fixture.session.requestBoost(fixture.racer))
                .describedAs("the cup is still in map one's lobby")
                .isFalse();

        fixture.run(tick -> { });
        fixture.racer.setFlyingWithElytra(true);

        assertThat(fixture.session.requestBoost(fixture.racer))
                .describedAs("the cup has finished")
                .isFalse();
        assertThat(rocketsAround(fixture.racer))
                .describedAs("nothing was lit either time")
                .isEmpty();
    }

    /** A racer who is not gliding gets no burn: Vanilla's rocket boosts a fall-flying entity only. */
    @Test
    void aBoostAskedForOnFootIsRefusedAndReachesTheSimulationNotAtAll(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        fixture.racer.setFlyingWithElytra(false);

        assertThat(fixture.session.requestBoost(fixture.racer)).isFalse();

        assertThat(fixture.countBoostedTicks(6)).isZero();
    }

    /**
     * A racer who disconnects mid-burn leaves no burn and no cooldown behind. Without it the next
     * player to be handed that UUID — a reconnect — comes back into the remains of a cooldown for a
     * boost they cannot see.
     */
    @Test
    void forgetsTheBurnOfARacerWhoDisconnectsMidBoost(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();
        assertThat(fixture.session.describe()).contains("boost burning");

        fixture.session.forget(fixture.racer.getUuid());

        assertThat(fixture.session.describe())
                .describedAs("neither burning nor cooling down")
                .contains("boost ready");
    }

    /**
     * A burn and its cooldown do not cross a map boundary.
     *
     * <p>The skip is what makes this bite, and it is the whole point of the arrangement. Played out
     * normally, map one's race phase is 20 ticks and the cooldown is 9, so it would have run out on
     * its own long before map two began — and a session that forgot nothing would look identical to
     * one that did. Skipping ends map one within a tick of the boost, so the cooldown is still
     * running when map two launches: a cooldown carried across refuses map two's first boost for a
     * rocket used on map one.
     */
    @Test
    void aBurnAndItsCooldownDoNotSurviveTheMapTheyWereLitOn(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();
        assertThat(fixture.session.requestSkip()).isTrue();

        fixture.runUntil(tick -> fixture.session.describe().contains("dune-run")
                && fixture.session.describe().contains("phase GAME"));

        assertThat(fixture.session.describe())
                .describedAs("map one's cooldown had %s of its %s ticks left when the map ended",
                        "several", RIDGE_BOOST.cooldownTicks())
                .contains("boost ready");
        assertThat(fixture.session.requestBoost(fixture.racer))
                .describedAs("map two's first boost is not refused by map one's cooldown")
                .isTrue();
    }

    /**
     * A restarted cup owes nobody the abandoned one's cooldown.
     *
     * <p>The restart is taken one tick after the boost, so the 9-tick cooldown is unambiguously still
     * running: a session that carried it over refuses the first boost of the cup it has just started,
     * which is a race that begins with a rocket the player cannot use and no explanation for it.
     */
    @Test
    void restartingTheCupDropsACooldownTakenOnTheRunThatWasAbandoned(Env env) throws IOException {
        Fixture fixture = start(env);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));
        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();

        fixture.session.start(true);
        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));

        assertThat(fixture.session.describe()).contains("boost ready");
        assertThat(fixture.session.requestBoost(fixture.racer)).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // The start, which now happens three seconds before the launch
    // ------------------------------------------------------------------------------------------

    /**
     * A map is entered once, however many callbacks want it entered.
     *
     * <p>Two of them do: the start countdown moves the racers three seconds early so the chunks and
     * the screen have settled by the launch, and {@code mapStarted} does it as well for a lobby too
     * short to have counted anything. Both call the same guarded step. Without the guard the racer
     * is told about the map twice on every map of every cup — which is the visible half; the
     * invisible half is a second teleport and a second fresh run.
     */
    @Test
    void aMapIsAnnouncedOnceEvenThoughTheCountdownAndTheLaunchBothEnterIt(Env env) throws IOException {
        Fixture fixture = start(env);
        Collector<SystemChatPacket> chat = fixture.connection.trackIncoming(SystemChatPacket.class);

        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));

        Component ridgeBanner = Messages.mapBanner(1, 2, RIDGE_RUN.name(), RIDGE_RUN.rings().size(),
                RIDGE_RUN.referenceTime());
        assertThat(chat.collect().stream().map(SystemChatPacket::message).filter(ridgeBanner::equals))
                .hasSize(1);
    }

    /**
     * The window the countdown opened, and the reason {@code mapStarted} still runs the transition.
     *
     * <p>Moving the racers three seconds early means a player can connect <em>between</em> the move
     * and the launch. Without the second transition that player is launched into a race holding no
     * run at all: they glide, they cross rings, and nothing counts any of it — which is the exact
     * failure the whole start sequence exists to remove, reintroduced by the fix for it.
     */
    @Test
    void aRacerWhoConnectsDuringTheCountdownStillGetsARun(Env env) throws IOException {
        Fixture fixture = start(env);

        Player[] latecomer = new Player[1];
        fixture.runUntil(tick -> {
            if (tick == 1) {
                latecomer[0] = fixture.addLatecomer(RIDGE, RIDGE_RUN);
            }
            return fixture.session.describe().contains("phase GAME");
        });

        assertThat(latecomer[0]).isNotNull();
        assertThat(fixture.runs.of(latecomer[0].getUuid()))
                .describedAs("a racer launched without a run flies a race nothing counts")
                .isPresent();
        assertThat(latecomer[0].isFlyingWithElytra()).isTrue();
    }

    // ------------------------------------------------------------------------------------------
    // What the racer is actually sent
    // ------------------------------------------------------------------------------------------

    /**
     * A ring is acknowledged out loud, in the tick it was crossed.
     *
     * <p>Sound is the channel this whole design leans on: it costs no screen space at 33 blocks a
     * second and has no read latency, and it is the only confirmation a racer gets per ring — there
     * is deliberately no chat line and no title. A tick loop that computed the pass and forgot to say
     * so would look identical from every other assertion in this class.
     *
     * <p>Identified by <strong>volume</strong>, which is not incidental. Ridge has one ring, so its
     * ring is also its last, and the last ring plays the same sound event a diamond result does.
     * The two differ by volume — 0.8 for the ring, 1.0 for the medal — so the ring's own packet is
     * the one this can name without the medal's standing in for it.
     */
    @Test
    void sendsTheRingSoundWhenARingIsCrossed(Env env) throws IOException {
        Fixture fixture = start(env);
        Collector<SoundEffectPacket> sounds = fixture.connection.trackIncoming(SoundEffectPacket.class);

        fixture.run(tick -> {
            if (fixture.session.describe().contains("ridge-run") && tick == 5) {
                fixture.teleport(RIDGE_SPAWN.plus(new Vec3(0, 8, 20)));
            }
        });

        assertThat(fixture.scoreOn(0).medal()).isNotEqualTo(MedalTier.DNF);
        assertThat(sounds.collect())
                .filteredOn(packet -> packet.soundEvent().key().asString()
                        .equals("minecraft:ui.toast.challenge_complete"))
                .filteredOn(packet -> packet.volume() == 0.8f)
                .describedAs("the ring's own acknowledgement, not the medal's")
                .hasSize(1);
    }

    /**
     * A racer is told what each map was worth and how the cup ended.
     *
     * <p>Chat is the surface with memory: it is the only thing still on screen when the next map's
     * countdown starts, and it is where a result goes when the title that carried it has faded. The
     * lines are compared against the ones {@code Messages} builds rather than against text, so this
     * stays a statement about the result being sent and not about its wording — the wording is
     * pinned once, in {@code MessageBundleTest}.
     */
    @Test
    void tellsTheRacerWhatTheMapWasWorthAndHowTheCupEnded(Env env) throws IOException {
        Fixture fixture = start(env);
        Collector<SystemChatPacket> chat = fixture.connection.trackIncoming(SystemChatPacket.class);

        fixture.run(tick -> {
            if (fixture.session.describe().contains("ridge-run") && tick == 5) {
                fixture.teleport(RIDGE_SPAWN.plus(new Vec3(0, 8, 20)));
            }
        });

        MapScore ridge = fixture.scoreOn(0);
        assertThat(ridge.medal()).isNotEqualTo(MedalTier.DNF);

        List<Component> lines = chat.collect().stream().map(SystemChatPacket::message).toList();
        assertThat(lines)
                .describedAs("the map result, with the placement bonus already awarded")
                .contains(Messages.mapResult(RIDGE_RUN.name(), ridge.ringPoints(), ridge.medalPoints(),
                        ridge.medal(), ridge.placementBonus(), ridge.total()));
        assertThat(lines)
                .describedAs("and the standings the cup ends on")
                .contains(Messages.cupHeading(CUP.name()));
    }

    /**
     * The countdown is on the screen before the launch, and so is the target.
     *
     * <p>Everything the start sequence is for happens in the lobby: the digits, the course under
     * them, and a boss bar naming the medal on offer while the racer is still standing on the spawn.
     * The arithmetic that decides <em>which</em> digit is {@code StartCountdownTest}'s; what this
     * asserts is that any of it is sent at all, which nothing else here would notice.
     *
     * <p>The fixture's lobby is two ticks, so one digit fits — which is the degraded case on purpose,
     * and the one that proves a short lobby shortens the count rather than delaying the race.
     */
    @Test
    void countsTheRacerDownBeforeTheLaunchAndPutsTheTargetOnScreenWithIt(Env env) throws IOException {
        Fixture fixture = start(env);
        Collector<SetTitleTextPacket> titles = fixture.connection.trackIncoming(SetTitleTextPacket.class);
        Collector<SetTitleSubTitlePacket> subtitles =
                fixture.connection.trackIncoming(SetTitleSubTitlePacket.class);
        Collector<SetTitleTimePacket> timings = fixture.connection.trackIncoming(SetTitleTimePacket.class);
        Collector<BossBarPacket> bars = fixture.connection.trackIncoming(BossBarPacket.class);

        fixture.runUntil(tick -> fixture.session.describe().contains("phase GAME"));

        assertThat(titles.collect().stream().map(SetTitleTextPacket::title))
                .describedAs("the last digit and then GO")
                .contains(Messages.countdownDigit(1), Messages.go());
        assertThat(subtitles.collect().stream().map(SetTitleSubTitlePacket::subtitle))
                .contains(Messages.countdownSubtitle(RIDGE_RUN.name(), RIDGE_RUN.rings().size(),
                        RIDGE_RUN.referenceTime()));
        assertThat(timings.collect())
                .describedAs("no fade-in: a digit that fades in is a digit that arrives late")
                .isNotEmpty()
                .allMatch(packet -> packet.fadeIn() == 0);
        assertThat(bars.collect())
                .describedAs("the target is on screen before the clock starts")
                .anyMatch(packet -> packet.action() instanceof BossBarPacket.AddAction);
    }

    /**
     * The boss bar comes down when the map does.
     *
     * <p>It is the run's memory — how much course is behind you, which medal is still reachable — and
     * all of that stops being true the moment the racer lands. Left up, it sits over the results
     * screen showing a countdown to a band that has already been awarded.
     */
    @Test
    void takesTheBossBarDownWhenAMapEnds(Env env) throws IOException {
        Fixture fixture = start(env);
        Collector<BossBarPacket> bars = fixture.connection.trackIncoming(BossBarPacket.class);

        fixture.runUntil(tick -> fixture.session.describe().contains("phase END"));

        assertThat(bars.collect())
                .describedAs("the bar goes up during the countdown")
                .anyMatch(packet -> packet.action() instanceof BossBarPacket.AddAction);
        assertThat(bars.collect())
                .describedAs("and comes down when the map ends")
                .anyMatch(packet -> packet.action() instanceof BossBarPacket.RemoveAction);
    }

    /**
     * A racer who has finished is shown the clock they finished on, not the one still running.
     *
     * <p>Their medal is decided, so a boss bar counting down a band they can no longer lose and an
     * action bar still ticking upward are both counting nothing — and the second one is worse,
     * because the number under their crosshair is no longer their time.
     *
     * <p>Two racers, because with one the phase ends the tick they finish and there is nothing left
     * to observe. The second racer never crosses anything, so the phase runs to its own limit and the
     * first racer's HUD keeps being rendered for fifteen more ticks with nothing left to change.
     */
    @Test
    void freezesAFinishersClockWhileTheRestOfTheFieldIsStillFlying(Env env) throws IOException {
        Fixture fixture = start(env);
        Collector<ActionBarPacket> actionBars = fixture.connection.trackIncoming(ActionBarPacket.class);

        // Stopped at the end of the first map, not run to the end of the cup: the second map would
        // put a fresh, running clock on the same action bar and the last packets would be its.
        fixture.runUntil(tick -> {
            if (tick == 1) {
                fixture.addLatecomer(RIDGE, RIDGE_RUN);
            }
            if (fixture.session.describe().contains("ridge-run") && tick == 5) {
                fixture.teleport(RIDGE_SPAWN.plus(new Vec3(0, 8, 20)));
            }
            return fixture.session.describe().contains("phase END");
        });

        assertThat(fixture.scoreOn(0).medal())
                .describedAs("the first racer has to have finished for there to be a clock to freeze")
                .isNotEqualTo(MedalTier.DNF);

        List<Component> sent = actionBars.collect().stream().map(ActionBarPacket::text).toList();
        assertThat(sent.getLast())
                .describedAs("the map ends by clearing the bar rather than leaving a stale clock to fade")
                .isEqualTo(Component.empty());

        List<Component> flightHuds = sent.stream().filter(text -> !Component.empty().equals(text)).toList();
        assertThat(flightHuds).describedAs("the HUD is rendered at 10 Hz for a whole race").hasSizeGreaterThan(6);
        List<Component> afterTheFlashHasCleared = flightHuds.subList(flightHuds.size() - 3, flightHuds.size());
        assertThat(afterTheFlashHasCleared)
                .describedAs("a finished racer's HUD has nothing left to change")
                .containsOnly(afterTheFlashHasCleared.getFirst());
    }

    // ------------------------------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------------------------------

    /** Everything one cup needs, assembled the way the composition root assembles it. */
    private final class Fixture {

        private final MapInstances worlds;
        private final RaceRuns runs;
        private final CupSession session;
        private final Player racer;
        private final TestConnection connection;
        private final List<Player> field;
        private final Env env;
        private int ticksPlayed;

        private Fixture(MapInstances worlds, RaceRuns runs, CupSession session, Player racer,
                TestConnection connection, List<Player> field, Env env) {
            this.worlds = worlds;
            this.runs = runs;
            this.session = session;
            this.racer = racer;
            this.connection = connection;
            this.field = field;
            this.env = env;
        }

        /** Connects a second racer into a cup that is already running, and adds them to the field. */
        Player addLatecomer(String world, MapDefinition map) {
            Pos spawn = spawnOf(map);
            Instance instance = worlds.forWorld(world);
            instance.loadChunk(spawn.chunkX(), spawn.chunkZ()).join();
            Player latecomer = env.createConnection().connect(instance, spawn);
            racers.add(latecomer);
            Racers.prepare(latecomer);
            field.add(latecomer);
            return latecomer;
        }

        /** Ticks until the cup finishes, or until the budget runs out. */
        void run(java.util.function.IntConsumer beforeEachTick) {
            runUntil(tick -> {
                beforeEachTick.accept(tick);
                return finished();
            });
        }

        /**
         * Ticks until {@code stop} answers true, or until the budget runs out. The budget is generous
         * and is a guard against a hang, not a length anything is asserted on.
         */
        void runUntil(IntPredicate stop) {
            for (int tick = 1; tick <= 400; tick++) {
                session.tick();
                ticksPlayed++;
                if (stop.test(tick)) {
                    return;
                }
            }
        }

        /**
         * Ticks {@code budget} times and counts the ticks on which the simulator was handed an active
         * firework boost for this racer. Reads {@code FlightTick.input()}, which is the value the
         * physics actually saw, rather than asking the tracker what it thinks it reported.
         */
        int countBoostedTicks(int budget) {
            int boosted = 0;
            for (int tick = 0; tick < budget; tick++) {
                session.tick();
                ticksPlayed++;
                Optional<FlightTick> simulated = session.lastSimulated(racer.getUuid());
                if (simulated.isPresent() && simulated.get().input().fireworkBoostActive()) {
                    boosted++;
                }
            }
            return boosted;
        }

        void teleport(Vec3 position) {
            racer.teleport(new Pos(position.x(), position.y(), position.z())).join();
        }

        boolean finished() {
            return session.cupFinished();
        }

        MapScore scoreOn(int mapIndex) {
            MapScore score = standingsOf().scoreOn(racer.getUuid(), mapIndex);
            assertThat(score).describedAs("score on map %s", mapIndex).isNotNull();
            return score;
        }

        List<MapScore> standings() {
            return standingsOf().of(racer.getUuid());
        }

        private CupStandings standingsOf() {
            return session.standings();
        }

        void release() {
            for (Player player : List.copyOf(field)) {
                player.remove(true);
                racers.remove(player);
            }
            worlds.close();
            if (CupSessionTest.this.worlds == worlds) {
                CupSessionTest.this.worlds = null;
            }
        }
    }

    private Fixture start(Env env) throws IOException {
        writeWorld(env, RIDGE, RIDGE_SPAWN, RIDGE_FLOOR);
        writeWorld(env, DUNE, DUNE_SPAWN, DUNE_FLOOR);

        MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        this.worlds = instances;
        RaceRuns runs = new RaceRuns();
        MapTransition transition = new MapTransition(instances, runs);

        Instance first = instances.forWorld(RIDGE);
        Pos spawn = spawnOf(RIDGE_RUN);
        first.loadChunk(spawn.chunkX(), spawn.chunkZ()).join();
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(first, spawn);
        racers.add(racer);
        Racers.prepare(racer);

        // Mutable, because a player joining while a cup is already running is a case worth testing
        // and a fixed field could not produce one.
        List<Player> field = new ArrayList<>(List.of(racer));
        CupSession session = CupSession.create(holder(), instances, transition, runs, new FlightTracker(),
                TIMINGS, STEP, () -> field);
        session.start(false);
        return new Fixture(instances, runs, session, racer, connection, field, env);
    }

    private static List<Entity> rocketsAround(Player racer) {
        return racer.getInstance().getEntities().stream()
                .filter(entity -> entity.getEntityType() == EntityType.FIREWORK_ROCKET)
                .toList();
    }

    private static Ring ring(int index, Vec3 center, int points) {
        return new Ring(index, center, new Vec3(0, 0, 1), 6.0, points, RingType.STANDARD);
    }

    private static Pos spawnOf(MapDefinition map) {
        return new Pos(map.spawn().x(), map.spawn().y(), map.spawn().z());
    }

    private static Block blockUnder(Player racer) {
        Pos position = racer.getPosition();
        return racer.getInstance().getBlock(position.blockX(), position.blockY() - 1, position.blockZ());
    }

    /**
     * Writes a world through a Falco loader of its own, the same round trip {@code MapInstancesTest}
     * and {@code MapTransitionTest} use, so the fixture proves the on-disk format rather than a
     * committed file the reading code was written against.
     */
    private void writeWorld(Env env, String world, Vec3 spawn, Block floor) throws IOException {
        InstanceManager instanceManager = env.process().instance();
        FalcoAnvilLoader writer = FalcoAnvilLoader.builder()
                .build(tempDir.resolve("worlds").resolve(world), DimensionType.OVERWORLD.key());
        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD, writer);
        int x = (int) Math.floor(spawn.x());
        int y = (int) Math.floor(spawn.y()) - 1;
        int z = (int) Math.floor(spawn.z());
        try {
            instance.loadChunk(x >> 4, z >> 4).join();
            instance.setBlock(x, y, z, floor);
            instance.saveChunksToStorage().join();
        } finally {
            instanceManager.unregisterInstance(instance);
            writer.close();
        }
    }
}
