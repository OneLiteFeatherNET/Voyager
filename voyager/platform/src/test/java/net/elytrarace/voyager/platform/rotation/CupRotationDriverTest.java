package net.elytrarace.voyager.platform.rotation;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuideLine;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.LogCapture;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.catalog.WorldOpener;
import net.elytrarace.voyager.race.rotation.CupRotationStore;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The armed cup and the worlds it needs, for one server in one JVM. Every test builds its own catalogue, holder, world
 * fake and store, so nothing is shared and no test depends on another. The clock is fixed, and no test waits.
 *
 * <p>The worlds are named distinctly per cup, except the one world that two cups share, so a release of the wrong world
 * shows in the event log. The event log records each open and discard in order, which is what the heap bound is about:
 * the previous cup's worlds go before the armed cup's worlds come.
 */
class CupRotationDriverTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);
    private static final String ALPHA = "elytrarace:alpha_cup";
    private static final String LEGACY = "elytrarace:legacy_cup";
    private static final String TWIN = "elytrarace:twin_cup";

    @Test
    void anArmedCupIsPinnedByTheNextRoundAndNotByTheRoundThatIsRunning() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));
        fixture.holder.promoteForNewRound();

        fixture.driver.armNext();

        assertThat(fixture.holder.current().cup().key().asString()).isEqualTo(ALPHA);
        assertThat(fixture.holder.promoteForNewRound().cup().key().asString()).isEqualTo(LEGACY);
    }

    @Test
    void theArmedCupIsWrittenToTheStoreByItsCanonicalName() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));

        fixture.driver.armNext();

        assertThat(fixture.store.written).containsExactly(LEGACY);
    }

    @Test
    void armingReleasesThePreviousCupsWorldsBeforeItOpensTheArmedCupsWorlds() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));

        fixture.driver.armNext();

        assertThat(fixture.worlds.events).containsExactly(
                "discard AlphaWorld", "open PalmWorld", "open DragonWorld", "open MemoriaWorld");
        assertThat(fixture.worlds.open).containsExactlyInAnyOrder("PalmWorld", "DragonWorld", "MemoriaWorld");
    }

    @Test
    void aWorldSharedBetweenTheTwoCupsIsKeptOpenAndNotReopened() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, TWIN), ALPHA, Set.of()));

        fixture.driver.armNext();

        assertThat(fixture.worlds.events).doesNotContain("discard AlphaWorld", "open AlphaWorld");
        assertThat(fixture.worlds.open).contains("AlphaWorld");
    }

    @Test
    void aCupThatNeverFinishesLeavesTheArmedCupWhereItWas() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));

        assertThat(fixture.driver.upcoming().cup().key().asString()).isEqualTo(ALPHA);
        assertThat(fixture.store.written).isEmpty();
        assertThat(fixture.worlds.events).isEmpty();
        assertThat(fixture.driver.armedName()).contains(ALPHA);
    }

    @Test
    void aWorldThatCannotBeOpenedLogsOneErrorAndTheRotationMovesOnToTheCupThatOpens() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));
        fixture.worlds.failOn("DragonWorld");

        try (LogCapture log = LogCapture.of(CupRotationDriver.class)) {
            fixture.driver.armNext();

            assertThat(log.errors()).singleElement().asString().contains("DragonWorld");
        }
        assertThat(fixture.driver.upcoming().cup().key().asString()).isEqualTo(ALPHA);
        assertThat(fixture.worlds.open).contains("AlphaWorld").doesNotContain("PalmWorld");
    }

    @Test
    void whenNoCupOpensTheLastCupStaysArmedAndTheFailureIsLogged() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));
        fixture.worlds.failOn("DragonWorld");
        fixture.worlds.failOn("AlphaWorld");

        try (LogCapture log = LogCapture.of(CupRotationDriver.class)) {
            fixture.driver.armNext();

            assertThat(log.errors()).isNotEmpty();
        }
        assertThat(fixture.driver.upcoming().cup().key().asString()).isEqualTo(ALPHA);
    }

    @Test
    void theReleaseWaitsWhileAPlayerStillStandsInTheWorldThatGoes() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));
        fixture.worlds.playersIn.add("AlphaWorld");

        fixture.driver.armNext();

        assertThat(fixture.worlds.events).isEmpty();
        assertThat(fixture.driver.upcoming().cup().key().asString()).isEqualTo(LEGACY);

        fixture.worlds.playersIn.clear();
        fixture.driver.tick();

        assertThat(fixture.worlds.events).containsExactly(
                "discard AlphaWorld", "open PalmWorld", "open DragonWorld", "open MemoriaWorld");
    }

    @Test
    void aCupSkippedAtBootIsNotArmedAndTheRotationPassesOverIt() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, TWIN, LEGACY), ALPHA, Set.of(TWIN)));

        fixture.driver.armNext();

        assertThat(fixture.driver.upcoming().cup().key().asString()).isEqualTo(LEGACY);
    }

    @Test
    void theArmedNameIsTheCanonicalNameOfTheCupThatWillBePlayed() {
        Fixture fixture = new Fixture(plan(List.of(ALPHA, LEGACY), ALPHA, Set.of()));
        assertThat(fixture.driver.armedName()).contains(ALPHA);

        fixture.driver.armNext();

        assertThat(fixture.driver.armedName()).contains(LEGACY);
    }

    @Test
    void withoutARotationArmingNeverMovesTheCupOrTheWorlds() {
        Fixture fixture = new Fixture(RotationPlan.single(Optional.of(ALPHA)));

        fixture.driver.armNext();

        assertThat(fixture.driver.rotates()).isFalse();
        assertThat(fixture.driver.armedName()).isEmpty();
        assertThat(fixture.holder.promoteForNewRound().cup().key().asString()).isEqualTo(ALPHA);
        assertThat(fixture.worlds.events).isEmpty();
        assertThat(fixture.store.written).isEmpty();
    }

    private static RotationPlan plan(List<String> rotation, String boot, Set<String> skipped) {
        return new RotationPlan(Optional.of(boot), rotation, skipped, List.of());
    }

    /** One server's worth of state: a catalogue with three cups whose worlds are distinct, except one shared pair. */
    private static final class Fixture {

        final CatalogHolder holder;
        final FakeWorlds worlds;
        final FakeStore store = new FakeStore();
        final CupRotationDriver driver;

        Fixture(RotationPlan plan) {
            CatalogSnapshot snapshot = snapshot();
            this.holder = new CatalogHolder(new LoadedCatalog(snapshot,
                    snapshot.cupByName(plan.boot().orElseThrow()).orElseThrow(), CLOCK.instant()));
            this.worlds = new FakeWorlds("AlphaWorld", "PalmWorld", "DragonWorld", "MemoriaWorld");
            this.worlds.open.add("AlphaWorld");
            this.driver = new CupRotationDriver(holder, worlds, store, CLOCK, plan);
        }

        private static CatalogSnapshot snapshot() {
            Map<String, MapDefinition> maps = Map.of(
                    "alpha_map", map("alpha_map", "AlphaWorld"),
                    "twin_map", map("twin_map", "AlphaWorld"),
                    "palm_map", map("palm_map", "PalmWorld"),
                    "dragon_map", map("dragon_map", "DragonWorld"),
                    "memoria_map", map("memoria_map", "MemoriaWorld"));
            Map<String, CupDefinition> cups = Map.of(
                    "alpha_cup", new CupDefinition("alpha_cup", List.of("alpha_map"), GameMode.RACE),
                    "twin_cup", new CupDefinition("twin_cup", List.of("twin_map"), GameMode.RACE),
                    "legacy_cup", new CupDefinition("legacy_cup",
                            List.of("palm_map", "dragon_map", "memoria_map"), GameMode.RACE));
            return new CatalogSnapshot(maps, cups);
        }

        private static MapDefinition map(String name, String world) {
            return new MapDefinition(name, world, Vec3.ZERO,
                    List.of(new Ring(0, Vec3.ZERO, new Vec3(0, 1, 0), 3, 10, RingType.STANDARD)),
                    Duration.ofSeconds(60), new BoostConfig(12, 25), new GuideLine(List.of(), 2, 1.0));
        }
    }

    /** Records each open and discard in order, and answers from a set of open worlds. */
    private static final class FakeWorlds implements WorldOpener {

        final Set<String> present;
        final Set<String> open = new HashSet<>();
        final Set<String> playersIn = new HashSet<>();
        final Set<String> failing = new HashSet<>();
        final List<String> events = new ArrayList<>();

        FakeWorlds(String... present) {
            this.present = Set.of(present);
        }

        void failOn(String world) {
            failing.add(world);
        }

        @Override
        public boolean holdsRegionData(String world) {
            return present.contains(world);
        }

        @Override
        public boolean isOpen(String world) {
            return open.contains(world);
        }

        @Override
        public void open(String world) {
            events.add("open " + world);
            if (failing.contains(world)) {
                throw new IllegalStateException("cannot open %s".formatted(world));
            }
            open.add(world);
        }

        @Override
        public void discard(String world) {
            events.add("discard " + world);
            open.remove(world);
        }

        @Override
        public boolean hasPlayers(String world) {
            return playersIn.contains(world);
        }

        @Override
        public boolean regionDataChanged(String world) {
            return false;
        }
    }

    /** Keeps each name written, in order. */
    private static final class FakeStore implements CupRotationStore {

        final List<String> written = new ArrayList<>();

        @Override
        public Optional<String> read() {
            return Optional.empty();
        }

        @Override
        public void write(String cup) {
            written.add(cup);
        }
    }
}
