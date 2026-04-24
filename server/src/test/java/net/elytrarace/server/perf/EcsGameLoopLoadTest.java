package net.elytrarace.server.perf;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.cup.MapDefinition;
import net.elytrarace.server.ecs.component.ActiveMapComponent;
import net.elytrarace.server.ecs.component.ElytraFlightComponent;
import net.elytrarace.server.ecs.component.FireworkBoostComponent;
import net.elytrarace.server.ecs.component.HudComponent;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.RingEffectComponent;
import net.elytrarace.server.ecs.component.RingTrackerComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.elytrarace.server.ecs.system.ElytraPhysicsSystem;
import net.elytrarace.server.ecs.system.FireworkBoostSystem;
import net.elytrarace.server.ecs.system.OutOfBoundsSystem;
import net.elytrarace.server.ecs.system.RingCollisionSystem;
import net.elytrarace.server.ecs.system.RingEffectSystem;
import net.elytrarace.server.ecs.system.RingVisualizationSystem;
import net.elytrarace.server.ecs.system.ScoreDisplaySystem;
import net.elytrarace.server.ecs.system.SplineVisualizationSystem;
import net.elytrarace.server.physics.Ring;
import net.elytrarace.server.physics.RingType;
import net.elytrarace.server.player.PlayerServiceImpl;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Load test / performance regression guard for the ECS game loop.
 *
 * <h2>Goal</h2>
 * Issue #119: verify that {@link EntityManager#update(float)} stays within the 50 ms
 * per-tick budget (20 TPS) with 10, 15, and 20 concurrent players.
 *
 * <h2>Methodology</h2>
 * <ul>
 *   <li>Build a full ECS world with the same systems as {@link net.elytrarace.server.game.GameOrchestrator}.</li>
 *   <li>Spawn N fake Minestom players via {@link Env#createPlayer(net.minestom.server.instance.Instance, Pos)}.</li>
 *   <li>Attach a {@link MapDefinition} with 20 rings (mixed types) to simulate a realistic map.</li>
 *   <li>Warm up the JIT for 200 ticks, then measure 300 ticks with {@link System#nanoTime()}.</li>
 *   <li>Compute p50, p95, p99 and max tick duration.</li>
 *   <li>Assert p99 &lt; 45 ms at 20 players, matching the acceptance criteria.</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * Fake players do not fly — their position and view angles are constant each tick, which
 * slightly under-represents live player load (network jitter, packet churn). The ECS work
 * itself (per-tick system processing, stream allocation, collision math) is identical to
 * production so the measurement is representative of the tick budget the ECS consumes.
 *
 * <p>The test is tagged {@code perf} so CI can optionally skip it on slow runners via
 * {@code -PexcludeTags=perf}; by default it runs to catch regressions early.
 */
@EnvTest
@Tag("perf")
class EcsGameLoopLoadTest {

    /** Warm-up ticks before measurement to let HotSpot JIT the hot paths. */
    private static final int WARMUP_TICKS = 200;

    /** Measured ticks per cohort (300 ticks = 15 s of simulated wall-clock time at 20 TPS). */
    private static final int MEASURE_TICKS = 300;

    /** Hard budget per tick (ms) — the 50 ms hard ceiling is the 20 TPS limit. */
    private static final double P99_BUDGET_MS = 45.0;

    /** Frame time target (ms): 1000 / 20 TPS. */
    private static final double TICK_DURATION_MS = 50.0;

    @Test
    void tickBudget10Players(Env env) {
        PercentileReport report = runLoadTest(env, 10);
        assertTickBudget(10, report);
    }

    @Test
    void tickBudget15Players(Env env) {
        PercentileReport report = runLoadTest(env, 15);
        assertTickBudget(15, report);
    }

    @Test
    void tickBudget20Players(Env env) {
        PercentileReport report = runLoadTest(env, 20);
        assertTickBudget(20, report);
    }

    /**
     * Verifies that a sustained 6000-tick (5 minute) run does not leak heap memory.
     * Allows for a 20% drift to account for JIT tiering and deferred collection;
     * a real leak would climb much faster.
     */
    @Test
    void heapStaysStableOverFiveMinutes(Env env) {
        Harness harness = buildHarness(env, 20);

        // Warmup + baseline
        for (int i = 0; i < WARMUP_TICKS; i++) {
            harness.entityManager.update(1f / 20f);
        }
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        long baseline = usedHeapBytes();

        final int fiveMinutesOfTicks = 5 * 60 * 20; // 6000
        for (int i = 0; i < fiveMinutesOfTicks; i++) {
            harness.entityManager.update(1f / 20f);
        }

        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        long afterRun = usedHeapBytes();

        long growthBytes = afterRun - baseline;
        double growthMb = growthBytes / (1024.0 * 1024.0);
        double baselineMb = baseline / (1024.0 * 1024.0);

        System.out.printf("[perf][heap] baseline=%.2f MB, after=%.2f MB, growth=%.2f MB%n",
                baselineMb, afterRun / (1024.0 * 1024.0), growthMb);

        // Tolerate up to 64 MB of drift over 5 minutes. A true leak (e.g., growing
        // passedRings set, unbounded HashMap) would typically grow >100 MB/5min at 20 players.
        assertThat(growthMb)
                .as("heap growth over 5 minutes at 20 players must stay below 64 MB")
                .isLessThan(64.0);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Harness
    // ────────────────────────────────────────────────────────────────────────────

    private PercentileReport runLoadTest(Env env, int playerCount) {
        Harness harness = buildHarness(env, playerCount);

        // Warm-up — let the JIT compile the hot loop
        for (int i = 0; i < WARMUP_TICKS; i++) {
            harness.entityManager.update(1f / 20f);
        }

        // Measure
        long[] tickNanos = new long[MEASURE_TICKS];
        for (int i = 0; i < MEASURE_TICKS; i++) {
            long t0 = System.nanoTime();
            harness.entityManager.update(1f / 20f);
            tickNanos[i] = System.nanoTime() - t0;
        }

        return PercentileReport.compute(tickNanos);
    }

    private static Harness buildHarness(Env env, int playerCount) {
        InstanceContainer instance = (InstanceContainer) env.createFlatInstance();

        EntityManager em = new EntityManager();

        // Game entity — holds ActiveMapComponent (required by RingCollision/OutOfBounds/visualization systems)
        Entity gameEntity = new Entity();
        ActiveMapComponent activeMap = new ActiveMapComponent();
        activeMap.setCurrentMap(buildTestMap());
        gameEntity.addComponent(activeMap);
        em.addEntity(gameEntity);

        // Register the full production system set, in production order.
        // Visualization systems are included even though they read MinecraftServer state
        // because they are on the hot tick path.
        PlayerServiceImpl playerService = new PlayerServiceImpl(instance);
        em.addSystem(new RingCollisionSystem(em));
        em.addSystem(new ElytraPhysicsSystem());
        em.addSystem(new FireworkBoostSystem());
        em.addSystem(new OutOfBoundsSystem(em, playerService));
        em.addSystem(new RingEffectSystem());
        em.addSystem(new RingVisualizationSystem(em));
        em.addSystem(new SplineVisualizationSystem());
        em.addSystem(new ScoreDisplaySystem());

        // Pre-load all chunks that spawn positions will fall into before creating players
        for (int i = 0; i < playerCount; i++) {
            int cx = Math.floorDiv((int) (i * 3), 16);
            instance.loadChunk(cx, 0).join();
        }

        // Spawn N player entities at distinct positions so they do not collide with each other
        List<Player> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            Pos spawn = new Pos(i * 3.0, 80.0, 0.0);
            Player player = env.createPlayer(instance, spawn);
            players.add(player);

            Entity e = new Entity();
            e.addComponent(new PlayerRefComponent(player.getUuid(), player));

            ElytraFlightComponent flight = new ElytraFlightComponent();
            flight.setFlying(true);
            // Give them a realistic velocity so physics does meaningful work each tick
            flight.setVelocity(new Vec(0, -0.1, 2.0));
            flight.setPreviousPosition(new Pos(spawn.x(), spawn.y() + 1.0, spawn.z() - 2.0));
            e.addComponent(flight);

            e.addComponent(new FireworkBoostComponent());
            e.addComponent(new RingTrackerComponent());
            e.addComponent(new ScoreComponent());
            e.addComponent(new RingEffectComponent());
            e.addComponent(new HudComponent(player));
            em.addEntity(e);
        }

        return new Harness(em, players);
    }

    /** Synthetic map with 20 rings arranged along the Z axis, mixed ring types. */
    private static MapDefinition buildTestMap() {
        List<Ring> rings = new ArrayList<>(20);
        RingType[] types = RingType.values();
        for (int i = 0; i < 20; i++) {
            double z = 25.0 + i * 20.0;
            RingType type = types[i % types.length];
            rings.add(new Ring(new Vec(0, 80, z), new Vec(0, 0, 1), 4.0, 10, type));
        }
        return new MapDefinition(
                "LoadTestMap",
                Path.of("/tmp/loadtest"),
                rings,
                new Pos(0, 80, 0));
    }

    private static long usedHeapBytes() {
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    private static void assertTickBudget(int playerCount, PercentileReport report) {
        System.out.printf(
                "[perf][ecs] players=%d ticks=%d  p50=%.3f ms  p95=%.3f ms  p99=%.3f ms  max=%.3f ms%n",
                playerCount, MEASURE_TICKS, report.p50, report.p95, report.p99, report.max);

        assertThat(report.p99)
                .as("p99 tick duration for %d players must stay below %.1f ms", playerCount, P99_BUDGET_MS)
                .isLessThan(P99_BUDGET_MS);

        // Soft check: if p50 blows past half the tick budget, something is wrong structurally.
        assertThat(report.p50)
                .as("p50 tick duration for %d players must stay well below tick duration", playerCount)
                .isLessThan(TICK_DURATION_MS / 2.0);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Data types
    // ────────────────────────────────────────────────────────────────────────────

    private record Harness(EntityManager entityManager, List<Player> players) {}

    /** Percentile summary of a sample of tick durations (nanoseconds). */
    private record PercentileReport(double p50, double p95, double p99, double max) {

        static PercentileReport compute(long[] samplesNanos) {
            long[] sorted = samplesNanos.clone();
            Arrays.sort(sorted);
            return new PercentileReport(
                    nsToMs(percentile(sorted, 0.50)),
                    nsToMs(percentile(sorted, 0.95)),
                    nsToMs(percentile(sorted, 0.99)),
                    nsToMs(sorted[sorted.length - 1]));
        }

        private static long percentile(long[] sorted, double q) {
            int idx = (int) Math.ceil(q * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
        }

        private static double nsToMs(long ns) {
            return ns / 1_000_000.0;
        }
    }
}
