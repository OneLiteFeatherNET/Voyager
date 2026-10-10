package net.elytrarace.voyager.server.command;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.collision.MinestomCollisionSpace;
import net.elytrarace.voyager.platform.cup.LivePlayerSampler;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.world.CurrentMapBlocks;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.catalog.ReloadOutcome;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.world.MapTransition;
import net.elytrarace.voyager.platform.world.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.elytrarace.voyager.server.game.CatalogReloadService;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /race reload} as the command manager sees it: the console reaches the reload, a player below the
 * operator level does not, and the command is there whether or not dev mode is on.
 */
@EnvTest
class RaceCommandReloadTest {

    private static final Duration STEP = Duration.ofMillis(50);
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(100));

    @TempDir
    Path tempDir;

    @Test
    void theConsoleReachesTheReload(Env env) {
        AtomicInteger reloads = new AtomicInteger();
        register(env, reloads, false);

        env.process().command().execute(new ConsoleSender(), "race reload");

        assertThat(reloads.get()).isEqualTo(1);
    }

    @Test
    void aPlayerBelowTheOperatorLevelDoesNotReachTheReload(Env env) {
        AtomicInteger reloads = new AtomicInteger();
        register(env, reloads, false);
        Player player = connect(env);
        player.setPermissionLevel(0);

        env.process().command().execute(player, "race reload");

        assertThat(reloads.get()).isZero();
    }

    @Test
    void theReloadIsRegisteredInDevModeToo(Env env) {
        AtomicInteger reloads = new AtomicInteger();
        register(env, reloads, true);

        env.process().command().execute(new ConsoleSender(), "race reload");

        assertThat(reloads.get()).isEqualTo(1);
    }

    private void register(Env env, AtomicInteger reloads, boolean devMode) {
        CatalogHolder holder = new CatalogHolder(catalog());
        CatalogReloadService service = new CatalogReloadService(() -> {
            reloads.incrementAndGet();
            return new ReloadOutcome.Rejected(List.of());
        }, holder, Runnable::run);
        MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        RaceRuns runs = new RaceRuns();
        CurrentMapBlocks blocks = new CurrentMapBlocks();
        FireworkBoostTracker boosts = new FireworkBoostTracker();
        FlightTickDriver flight = new FlightTickDriver(new LivePlayerSampler(List::of, boosts), new FlightTracker(),
                new MinestomCollisionSpace(blocks));
        CupSession session = new CupSession(holder, instances, new MapTransition(instances, runs), runs, flight,
                blocks, boosts, TIMINGS, STEP, List::of);
        env.process().command().register(new RaceCommand(session, devMode, service));
    }

    private static Player connect(Env env) {
        Instance instance = env.createFlatInstance();
        return env.createConnection().connect(instance, new Pos(0, 42, 0));
    }

    private static LoadedCatalog catalog() {
        MapDefinition map = new MapDefinition("ridge", "ridge-world", new Vec3(0, 64, 0),
                List.of(new Ring(0, new Vec3(0, 64, 10), new Vec3(0, 0, 1), 3, 10, RingType.STANDARD)),
                Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        CupDefinition cup = new CupDefinition("tour", List.of("ridge"), GameMode.RACE);
        return new LoadedCatalog(new CatalogSnapshot(Map.of("ridge", map), Map.of("tour", cup)), cup, Instant.EPOCH);
    }
}
