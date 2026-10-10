package net.elytrarace.voyager.server;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.GameMode;
import net.elytrarace.voyager.api.race.GuidePoint;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.Ring;
import net.elytrarace.voyager.api.race.RingType;
import net.elytrarace.voyager.platform.catalog.CatalogLoader;
import net.elytrarace.voyager.platform.catalog.CatalogSnapshot;
import net.elytrarace.voyager.race.line.RacingLine;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** The BOOST rings of the shipped course: the three longest straight segments, see the map's notes. */
    private static final Set<Integer> BOOST_RINGS = Set.of(0, 18, 27);

    @Test
    void theCommittedRacecourseLoadsThroughTheRealCatalogue() {
        MapDefinition map = CatalogLoader.load(RESOURCES)
                .mapByName("elytraraceblueandred").orElseThrow();

        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
        assertThat(map.spawn()).isEqualTo(new Vec3(109, -62, 54));
        assertThat(map.rings()).hasSize(35);
        assertThat(map.rings()).extracting(Ring::index)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 35).boxed().toList());
    }

    @Test
    void everyRingOfTheCommittedRacecourseCarriesAUnitNormalAndTheOctagonRadius() {
        MapDefinition map = CatalogLoader.load(RESOURCES)
                .mapByName("elytraraceblueandred").orElseThrow();

        for (Ring ring : map.rings()) {
            assertThat(ring.normal().length())
                    .as("normal length of ring %s", ring.index())
                    .isCloseTo(1.0, Offset.offset(1e-12));
            assertThat(ring.radius())
                    .as("radius of ring %s", ring.index())
                    .isCloseTo(Math.sqrt(13), Offset.offset(1e-12));
            assertThat(ring.type())
                    .as("type of ring %s", ring.index())
                    .isEqualTo(BOOST_RINGS.contains(ring.index()) ? RingType.BOOST : RingType.STANDARD);
        }
    }

    @Test
    void theFirstRingFacesTheWayAPlayerLeavesTheSpawn() {
        // The spawn sits at x = 109 and ring 0's centre at x = 85, same z, so the course is entered
        // travelling west. The stored normal is what decides which way through the ring counts, and
        // this is the one ring whose answer can be checked against something outside the ring data
        // itself — the authored spawn in the world's level.dat.
        MapDefinition map = CatalogLoader.load(RESOURCES)
                .mapByName("elytraraceblueandred").orElseThrow();
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
        MapDefinition map = CatalogLoader.load(RESOURCES)
                .mapByName("elytraraceblueandred").orElseThrow();

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
        MapDefinition map = CatalogLoader.load(RESOURCES)
                .mapByName("elytraraceblueandred").orElseThrow();

        assertThat(map.boostConfig()).isEqualTo(new BoostConfig(30, 40));
        assertThat(map.boostConfig().burnDurationTicks()).isEqualTo(BoostConfig.VANILLA_BURN_TICKS);
        assertThat(map.boostConfig().cooldownTicks() * 50L)
                .describedAs("the old file's cooldownMs, back out of the ticks it was converted into")
                .isEqualTo(2_000L);
    }

    @Test
    void theCommittedCupPlaysTheCommittedMap() {
        CatalogSnapshot catalog = CatalogLoader.load(RESOURCES);

        CupDefinition cup = catalog.cupByName("alpha_cup").orElseThrow();

        assertThat(cup.mode()).isEqualTo(GameMode.RACE);
        assertThat(cup.mapNames()).containsExactly("elytraraceblueandred");
        // load() runs the cross-catalogue check, so this returning at all is the assertion that the
        // committed cup resolves against the committed map.
        assertThat(catalog.mapByName("elytraraceblueandred")).isPresent();
    }
    /**
     * The sixteen guide points of the committed course, and the pair that share a gap.
     *
     * <p>These order indices are the ones the old {@code guides.json} carries, and the conversion
     * carries them across unchanged. 2450 and 2475 both sit between rings 24 and 25 — the case that
     * makes "more than one guide per gap" a rule rather than a hypothetical, and the case an
     * implementation that keyed guides by gap loses half of without saying anything.
     */
    @Test
    void theCommittedRacecourseCarriesTheSixteenGuidePointsItWasBuiltWith() {
        MapDefinition map = committedCourse();

        assertThat(map.guideLine().points()).extracting(GuidePoint::orderIndex)
                .containsExactly(150, 250, 350, 450, 550, 650, 850, 950, 1_050, 1_450, 1_950, 2_150,
                        2_450, 2_475, 2_550, 2_650);
        assertThat(map.guideLine().points()).extracting(GuidePoint::afterRing)
                .contains(24, 24)
                .doesNotContain(-1, 34);
        // Seeded, and stated here so a change to either is a deliberate edit of this test as well.
        assertThat(map.guideLine().lookAheadRings()).isEqualTo(2);
        assertThat(map.guideLine().particleSpacing()).isEqualTo(1.0);
    }

    /**
     * Every guide bends the line without materially lengthening it: going ring, guide, ring is
     * between 1.00 and 1.20 times going ring to ring directly.
     *
     * <p>This is the measurement that says the interleaving rule is the right one. A guide read into
     * the wrong gap is still a valid file and still draws a line — it just sends it across the map and
     * back, which this factor sees immediately and nothing else in the data does. The band is the one
     * measured over all sixteen before any of this was written; the worst of them is 1.196, at order
     * index 950, where two rings are only 20 blocks apart.
     */
    @Test
    void everyGuidePointOfTheCommittedRacecourseBendsItsGapWithoutLengtheningIt() {
        MapDefinition map = committedCourse();

        for (GuidePoint guide : map.guideLine().points()) {
            Vec3 before = map.rings().get(guide.afterRing()).center();
            Vec3 after = map.rings().get(guide.afterRing() + 1).center();
            double viaTheGuide = before.distanceTo(guide.position()) + guide.position().distanceTo(after);

            assertThat(viaTheGuide / before.distanceTo(after))
                    .as("detour factor of the guide at order index %s", guide.orderIndex())
                    .isBetween(1.00, 1.20);
        }
    }

    /**
     * The line the racers are shown, over the real course: through every ring, 1666 blocks for a
     * course that is 1588 straight, and a stretch a racer can afford to be sent.
     *
     * <p>The last of those is the budget, and it is here rather than in a comment because it is a
     * property of this course's geometry and nothing else: a particle is a packet, so the widest
     * two-ring stretch of this line is the worst tick one racer costs. 198 packets, on one tick in
     * four — about 25 a tick averaged. Halving {@code particleSpacing} in the file above doubles both
     * numbers, which is the one edit that can make this feature unaffordable.
     */
    @Test
    void theRacingLineOfTheCommittedRacecourseRunsThroughItAndFitsInTheBudget() {
        MapDefinition map = committedCourse();
        RacingLine line = RacingLine.of(map);

        assertThat(line.ringCount()).isEqualTo(35);
        for (Ring ring : map.rings()) {
            assertThat(line.points().get(line.ringPoints().get(ring.index())))
                    .as("ring %s on the line", ring.index())
                    .isEqualTo(ring.center());
        }
        assertThat(line.points()).containsAll(
                map.guideLine().points().stream().map(GuidePoint::position).toList());

        double straight = 0.0;
        for (int i = 1; i < map.rings().size(); i++) {
            straight += map.rings().get(i - 1).center().distanceTo(map.rings().get(i).center());
        }
        double drawn = 0.0;
        for (int i = 1; i < line.points().size(); i++) {
            drawn += line.points().get(i - 1).distanceTo(line.points().get(i));
        }
        assertThat(straight).isCloseTo(1_587.9, Offset.offset(0.1));
        assertThat(drawn / straight).isBetween(1.00, 1.20);

        int widestStretch = 0;
        for (int passed = 0; passed < line.ringCount(); passed++) {
            int lastRing = Math.min(passed + map.guideLine().lookAheadRings(), line.ringCount() - 1);
            widestStretch = Math.max(widestStretch, line.between(passed, lastRing).size());
        }
        assertThat(widestStretch)
                .as("particles one racer is sent on the worst refresh of this course")
                .isBetween(150, 220);
    }

    private static MapDefinition committedCourse() {
        return CatalogLoader.load(RESOURCES).mapByName("elytraraceblueandred").orElseThrow();
    }

}
