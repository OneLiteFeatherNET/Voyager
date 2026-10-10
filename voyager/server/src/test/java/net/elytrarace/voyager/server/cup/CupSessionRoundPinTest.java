package net.elytrarace.voyager.server.cup;

import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.platform.flight.FlightTracker;
import net.elytrarace.voyager.platform.world.MapInstances;
import net.elytrarace.voyager.platform.cup.MapTransition;
import net.elytrarace.voyager.platform.cup.RaceRuns;
import net.elytrarace.voyager.race.flow.RaceTimings;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A round reads one catalogue for its whole length. An offer made while a round runs waits for the next
 * {@link CupSession#start}; the round itself keeps the catalogue it started with.
 *
 * <p>No player and no tick is needed: the pin is a fact about what {@code start} took, and the round's own
 * tests cover what it does with it.
 */
@EnvTest
class CupSessionRoundPinTest {

    private static final Duration STEP = Duration.ofMillis(50);
    private static final RaceTimings TIMINGS = new RaceTimings(
            Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(100));

    @TempDir
    Path tempDir;

    @Test
    void aRoundPinsTheCatalogueItStartedWithAndALaterOfferWaitsForTheNextRound(Env env) {
        LoadedCatalog first = catalog("tour-one", "ridge");
        LoadedCatalog second = catalog("tour-two", "dune");
        CatalogHolder holder = new CatalogHolder(first);
        CupSession session = session(env, holder);
        session.start(false);

        holder.offer(second);

        assertThat(session.pinned()).isEqualTo(first);
        assertThat(session.cup().name()).isEqualTo("tour-one");
    }

    @Test
    void theNextRoundPinsTheCatalogueThatWasOfferedDuringTheLastOne(Env env) {
        LoadedCatalog first = catalog("tour-one", "ridge");
        LoadedCatalog second = catalog("tour-two", "dune");
        CatalogHolder holder = new CatalogHolder(first);
        CupSession session = session(env, holder);
        session.start(false);
        holder.offer(second);

        session.start(false);

        assertThat(session.pinned()).isEqualTo(second);
        assertThat(session.cup().name()).isEqualTo("tour-two");
        assertThat(holder.pending()).isEmpty();
    }

    @Test
    void beforeTheFirstRoundTheSessionShowsTheBootCatalogue(Env env) {
        LoadedCatalog boot = catalog("tour-one", "ridge");
        CupSession session = session(env, new CatalogHolder(boot));

        assertThat(session.pinned()).isEqualTo(boot);
        assertThat(session.cup().name()).isEqualTo("tour-one");
    }

    @Test
    void describeSaysAReloadWaitsWhileOneIsPending(Env env) {
        CatalogHolder holder = new CatalogHolder(catalog("tour-one", "ridge"));
        CupSession session = session(env, holder);
        session.start(false);

        holder.offer(catalog("tour-two", "dune"));

        assertThat(session.describe()).contains("a reload waits for the next round");
    }

    @Test
    void describeBreaksThePendingReloadLineInsteadOfPrintingAFormatMarker(Env env) {
        CatalogHolder holder = new CatalogHolder(catalog("tour-one", "ridge"));
        CupSession session = session(env, holder);
        session.start(false);

        holder.offer(catalog("tour-two", "dune"));

        assertThat(session.describe())
                .doesNotContain("%n")
                .contains("\n  a reload waits for the next round\n");
    }

    @Test
    void describeSaysNothingIsWaitingWhenNoReloadWasOffered(Env env) {
        CupSession session = session(env, new CatalogHolder(catalog("tour-one", "ridge")));
        session.start(false);

        assertThat(session.describe()).doesNotContain("a reload waits for the next round");
    }

    private CupSession session(Env env, CatalogHolder holder) {
        MapInstances instances = new MapInstances(env.process().instance(), tempDir.resolve("worlds"));
        RaceRuns runs = new RaceRuns();
        return CupWiring.assemble(holder, instances, new MapTransition(instances, runs), runs, new FlightTracker(),
                TIMINGS, STEP, List::of).session();
    }

    private static LoadedCatalog catalog(String cupName, String mapName) {
        MapDefinition map = new MapDefinition(mapName, mapName + "-world", new Vec3(0, 64, 0),
                List.of(new Ring(0, new Vec3(0, 64, 10), new Vec3(0, 0, 1), 3, 10, RingType.STANDARD)),
                Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        CupDefinition cup = new CupDefinition(cupName, List.of(mapName), GameMode.RACE);
        return new LoadedCatalog(new CatalogSnapshot(Map.of(mapName, map), Map.of(cupName, cup)), cup, Instant.EPOCH);
    }
}
