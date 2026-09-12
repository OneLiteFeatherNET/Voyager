package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogConsistency;
import net.elytrarace.voyager.platform.catalog.JsonCupCatalog;
import net.elytrarace.voyager.platform.catalog.JsonMapCatalog;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The data, not the code. Every other test of the catalogue writes its own fixtures on purpose — a
 * test that reads the shipped file passes for the wrong reason the day somebody edits it, because it
 * is then measuring the file rather than the reader. This one is the deliberate exception, and the
 * only one: it asserts that the {@code ElytraraceBlueAndRed} committed to this module is the course
 * that was converted, and that it still loads through the real catalogue.
 *
 * <p>The values are the measurements the conversion was checked against: 35 rings from the recorded
 * portals, indices renumbered from the recorded 1..35 to the 0..34 a progress tracker reads by, every
 * normal derived from the rim and unit length, every radius {@code sqrt(13)} because every ring is
 * the same regular octagon, and the spawn read out of the world's {@code level.dat}.
 *
 * <p>It is also the one thing that would catch the writer in {@code tools/map-converter} and the
 * adapters in {@code voyager-platform} drifting apart: the format is hand-written on both sides, so
 * nothing but a round trip over a real file can say they still agree.
 */
class CommittedMapDataTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void theCommittedRacecourseLoadsThroughTheRealCatalogue() {
        MapDefinition map = new JsonMapCatalog(RESOURCES.resolve("maps"))
                .byName("elytraraceblueandred").orElseThrow();

        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(map.spawn()).isEqualTo(new Vec3(109, -62, 54));
        assertThat(map.rings()).hasSize(35);
        assertThat(map.rings()).extracting(Ring::index)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 35).boxed().toList());
    }

    @Test
    void everyRingOfTheCommittedRacecourseCarriesAUnitNormalAndTheOctagonRadius() {
        MapDefinition map = new JsonMapCatalog(RESOURCES.resolve("maps"))
                .byName("elytraraceblueandred").orElseThrow();

        for (Ring ring : map.rings()) {
            assertThat(ring.normal().length())
                    .as("normal length of ring %s", ring.index())
                    .isCloseTo(1.0, Offset.offset(1e-12));
            assertThat(ring.radius())
                    .as("radius of ring %s", ring.index())
                    .isCloseTo(Math.sqrt(13), Offset.offset(1e-12));
            assertThat(ring.type()).isEqualTo(RingType.STANDARD);
        }
    }

    @Test
    void theFirstRingFacesTheWayAPlayerLeavesTheSpawn() {
        // The spawn sits at x = 109 and ring 0's centre at x = 85, same z, so the course is entered
        // travelling west. The stored normal is what decides which way through the ring counts, and
        // this is the one ring whose answer can be checked against something outside the ring data
        // itself — the authored spawn in the world's level.dat.
        MapDefinition map = new JsonMapCatalog(RESOURCES.resolve("maps"))
                .byName("elytraraceblueandred").orElseThrow();
        Ring first = map.rings().getFirst();

        assertThat(first.center()).isEqualTo(new Vec3(85, -54, 54));
        assertThat(first.normal()).isEqualTo(new Vec3(-1, 0, 0));
        assertThat(first.center().minus(map.spawn()).dot(first.normal())).isPositive();
    }

    @Test
    void theCommittedReferenceTimeAndRingScoresAreTheSeedsTheConversionWrote() {
        // Provisional, and asserted anyway: these are the three values the old data never carried,
        // and the notes in the file say as much. Pinning them means a balancing pass is a visible
        // change to this test rather than a number that moved without anyone noticing.
        MapDefinition map = new JsonMapCatalog(RESOURCES.resolve("maps"))
                .byName("elytraraceblueandred").orElseThrow();

        assertThat(map.referenceTime()).isEqualTo(Duration.ofSeconds(60));
        assertThat(map.rings()).extracting(Ring::points).containsOnly(10);
    }

    /**
     * The boost tuning of the shipped course, and the two different places its two numbers came from.
     *
     * <p>40 ticks is the old {@code map.json}'s own {@code cooldownMs: 2000}, converted once — real
     * authored data about this course, carried across rather than re-decided. 30 ticks is
     * {@code BoostConfig.VANILLA_BURN_TICKS}, seeded because the old file carried no burn at all.
     * Pinned here so that a balancing pass is a visible change to this test rather than a number that
     * moved, exactly as the reference time and the ring scores above are.
     */
    @Test
    void theCommittedBoostTuningIsTheOldFilesCooldownAndTheVanillaDerivedBurn() {
        MapDefinition map = new JsonMapCatalog(RESOURCES.resolve("maps"))
                .byName("elytraraceblueandred").orElseThrow();

        assertThat(map.boostConfig()).isEqualTo(new BoostConfig(30, 40));
        assertThat(map.boostConfig().burnDurationTicks()).isEqualTo(BoostConfig.VANILLA_BURN_TICKS);
        assertThat(map.boostConfig().cooldownTicks() * 50L)
                .describedAs("the old file's cooldownMs, back out of the ticks it was converted into")
                .isEqualTo(2_000L);
    }

    @Test
    void theCommittedCupPlaysTheCommittedMap() {
        JsonMapCatalog maps = new JsonMapCatalog(RESOURCES.resolve("maps"));
        JsonCupCatalog cups = new JsonCupCatalog(RESOURCES.resolve("cups"));

        CupDefinition cup = cups.byName("test_cup").orElseThrow();

        assertThat(cup.mode()).isEqualTo(GameMode.RACE);
        assertThat(cup.mapNames()).containsExactly("elytraraceblueandred");
        assertThatCode(() -> CatalogConsistency.requireEveryCupMapResolves(cups, maps))
                .doesNotThrowAnyException();
    }
}
