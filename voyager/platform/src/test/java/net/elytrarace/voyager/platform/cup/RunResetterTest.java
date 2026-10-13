package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.FlightTracker;

import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.race.progress.RingProgress;
import net.elytrarace.voyager.race.reset.ResetCause;
import net.elytrarace.voyager.race.reset.ResetPlan;
import net.elytrarace.voyager.race.reset.RunReset;
import net.elytrarace.voyager.race.run.RaceRun;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SetTitleTextPacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link RunResetter} on a live server: where the racer lands, what they hold afterwards, and what they are
 * told. Every test builds its own world and racer, and drives its ticks explicitly.
 */
@EnvTest
class RunResetterTest {

    /** The tuning the reset racer is given their rockets back under: the default count, a 30-tick burn. */
    private static final BoostConfig TUNING = new BoostConfig(30, 40);

    /**
     * The checkpoint the racer last passed: its centre is where a reset puts them, and its normal is where they face.
     * A reset targets the last checkpoint, not the last ring of any type.
     */
    private static final Ring LAST = new Ring(0, new Vec3(0.5, 70.0, 0.5), new Vec3(0.0, 0.0, 1.0), 5.0, 7,
            RingType.CHECKPOINT);

    /** The ring after it, not yet passed, so a reset target is never the next ring. */
    private static final Ring NEXT = new Ring(1, new Vec3(0.5, 70.0, 20.5), new Vec3(0.0, 0.0, 1.0), 5.0, 9,
            RingType.BOOST);

    private static final MapDefinition MAP = new MapDefinition("reset-run", "reset_arena",
            new Vec3(0.5, 60.0, 0.5), List.of(LAST, NEXT), Duration.ofSeconds(4), new BoostConfig(12, 25),
            new GuideLine(List.of(), 2, 1.0));

    private static final Duration STEP = Duration.ofMillis(50);

    /** One racer, and the collaborators a reset touches, sharing one flight driver. */
    private record Fixture(Player racer, RaceRuns runs, FireworkBoostTracker boosts, RunResetter resetter) {
    }

    private static Fixture fixtureFor(Player racer, Instance instance) {
        RaceRuns runs = new RaceRuns();
        runs.startFresh(racer.getUuid());
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(
                new LivePlayerSampler(() -> List.of(racer), boosts),
                new FlightTracker(),
                new MinestomCollisionSpace(instance));
        return new Fixture(racer, runs, boosts, new RunResetter(runs, boosts, flight));
    }

    private static Fixture fixture(Env env) {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        Player racer = env.createPlayer(instance, new Pos(0.5, 60, 0.5));
        return fixtureFor(racer, instance);
    }

    /** A run that has passed the first ring, on the tick after it, and is still gliding. */
    private static RaceRun passedFirstRing() {
        return new RaceRun(new RingProgress(1, 0), new Vec3(0.5, 70.0, 0.4), List.of(3), Optional.empty(),
                LAST, true);
    }

    private static ResetPlan planFor(RaceRun run) {
        return RunReset.plan(MAP, run, ResetCause.OUT_OF_BOUNDS);
    }

    @Test
    void theRacerIsPlacedBehindAndAboveTheCheckpointPassed(Env env) {
        Fixture f = fixture(env);

        f.resetter().reset(f.racer(), planFor(passedFirstRing()), TUNING);

        // The centre of the checkpoint is (0.5, 70, 0.5) and its normal is +z: one block back, one block up.
        assertThat(f.racer().getPosition().samePoint(new Pos(0.5, 71.0, -0.5)))
                .describedAs("behind and above the centre of the checkpoint, so the racer is airborne")
                .isTrue();
    }

    /** A checkpoint reset leaves the racer gliding, with the equipment in hand. */
    @Test
    void aResetLeavesTheRacerGliding(Env env) {
        Fixture f = fixture(env);

        f.resetter().reset(f.racer(), planFor(passedFirstRing()), TUNING);

        assertThat(f.racer().isFlyingWithElytra())
                .describedAs("the racer is placed gliding, with no impulse")
                .isTrue();
        assertThat(f.racer().getEquipment(EquipmentSlot.CHESTPLATE).material())
                .describedAs("the elytra is in hand")
                .isEqualTo(Material.ELYTRA);
        assertThat(f.racer().getInventory().getItemStack(0).material())
                .describedAs("the rockets are in the hotbar")
                .isEqualTo(Material.FIREWORK_ROCKET);
    }

    /** A reset to the spawn is grounded, as at GO: the racer is not gliding and stands at the spawn. */
    @Test
    void aResetToTheSpawnLeavesTheRacerOnFootAtTheSpawn(Env env) {
        Fixture f = fixture(env);
        RaceRun passedNoCheckpoint = new RaceRun(new RingProgress(1, -1), new Vec3(0.5, 70.0, 0.4),
                List.of(3), Optional.empty(), LAST, true);

        f.resetter().reset(f.racer(), planFor(passedNoCheckpoint), TUNING);

        assertThat(f.racer().getPosition().samePoint(new Pos(0.5, 60.0, 0.5)))
                .describedAs("the map's spawn")
                .isTrue();
        assertThat(f.racer().isFlyingWithElytra())
                .describedAs("grounded at the spawn: the racer takes off by deploying the elytra")
                .isFalse();
    }

    /** No impulse: the reset sends a zero velocity and nothing else, so the racer is at rest while gliding. */
    @Test
    void aResetAddsNoImpulse(Env env) {
        Fixture f = fixture(env);

        f.resetter().reset(f.racer(), planFor(passedFirstRing()), TUNING);

        assertThat(f.racer().getVelocity())
                .describedAs("the only velocity a reset sends is zero")
                .isEqualTo(Vec.ZERO);
        assertThat(f.racer().isFlyingWithElytra()).isTrue();
    }

    /** A racer who falls into a reset must not carry the fall into the target: the reset zeroes the velocity. */
    @Test
    void aResetZeroesTheRacersVelocity(Env env) {
        Fixture f = fixture(env);
        f.racer().setVelocity(new Vec(0, -20, 0));

        f.resetter().reset(f.racer(), planFor(passedFirstRing()), TUNING);

        assertThat(f.racer().getVelocity())
                .describedAs("the fall is not carried into the target")
                .isEqualTo(Vec.ZERO);
    }

    /**
     * A racer who touches the ground straight after a reset is not landing: they have no airborne grace yet, and the
     * reset cooldown is running.
     */
    @Test
    void aResetRacerTouchingTheGroundIsNotALanding(Env env) {
        Fixture f = fixture(env);

        f.resetter().reset(f.racer(), planFor(passedFirstRing()), TUNING);

        RaceRun afterReset = f.runs().of(f.racer().getUuid()).orElseThrow();
        assertThat(RunReset.isLanded(afterReset, false, true))
                .describedAs("on the ground and no longer gliding, one tick after the reset")
                .isFalse();
    }

    @Test
    void theRacerFacesAlongTheFlowNormalOfTheTargetRing(Env env) {
        Fixture f = fixture(env);

        f.resetter().reset(f.racer(), planFor(passedFirstRing()), TUNING);

        assertThat(f.racer().getPosition().direction().z())
                .describedAs("the normal of ring 0 is +z, so the racer looks along +z")
                .isCloseTo(1.0, within(1e-6));
    }

    @Test
    void theRunBecomesThePlannedRun(Env env) {
        Fixture f = fixture(env);
        ResetPlan plan = planFor(passedFirstRing());

        f.resetter().reset(f.racer(), plan, TUNING);

        assertThat(f.runs().of(f.racer().getUuid())).contains(plan.run());
    }

    @Test
    void anActiveBurnIsCancelledAndTheCooldownIsKept(Env env) {
        Fixture f = fixture(env);
        f.boosts().requestBoost(f.racer().getUuid(), new BoostConfig(4, 9), true);

        f.resetter().reset(f.racer(), planFor(passedFirstRing()), TUNING);

        assertThat(f.boosts().burning(f.racer().getUuid())).isFalse();
        assertThat(f.boosts().cooldownTicksRemaining(f.racer().getUuid())).isEqualTo(9);
    }

    @Test
    void theTitleAndTheSoundAreSentInTheTickOfTheReset(Env env) throws Exception {
        Instance instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        TestConnection connection = env.createConnection();
        Player racer = connection.connect(instance, new Pos(0.5, 60, 0.5));
        Fixture f = fixtureFor(racer, instance);
        Collector<SetTitleTextPacket> titles = connection.trackIncoming(SetTitleTextPacket.class);
        Collector<SoundEffectPacket> sounds = connection.trackIncoming(SoundEffectPacket.class);

        f.resetter().reset(racer, planFor(passedFirstRing()), TUNING);
        env.tick();

        assertThat(titles.collect())
                .describedAs("the reason and the target are shown in the tick the reset is detected")
                .isNotEmpty();
        assertThat(sounds.collect())
                .describedAs("the reset sound is played in the tick the reset is detected")
                .isNotEmpty();
    }
}
