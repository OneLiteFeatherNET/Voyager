package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.effect.RingEffectTuning;
import net.elytrarace.voyager.api.race.exception.InvalidCatalogKeyException;
import net.elytrarace.voyager.api.race.exception.InvalidMapException;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapDefinitionTest {

    private static Ring ring(int index, double z) {
        return new Ring(index, new Vec3(1.5, 64.0, z), new Vec3(0.0, 0.0, 1.0), 5.0, 10, RingType.STANDARD);
    }

    private static final List<Ring> RINGS = List.of(ring(0, 100.0), ring(1, 200.0));

    /**
     * A 14-tick burn and a 33-tick cooldown: neither is the Vanilla-derived default, and the two are
     * not a multiple of one another, so a test that read one field where it meant the other — or fell
     * back to a built-in default — reads a value that is nowhere else in this file.
     */
    private static final BoostConfig BOOST = new BoostConfig(14, 33);

    /**
     * Three rings ahead and 1.75 blocks between particles: neither is a value any default in this
     * repository uses, so a map that fell back to one instead of keeping what it was handed shows up.
     */
    private static final GuideLine NO_GUIDES = new GuideLine(List.of(), 3, 1.75);

    /** A map file without {@code ringEffects} is built with the factors that were hard-coded before. */
    @Test
    void aMapWithoutRingEffectsGetsTheDefaultTuning() {
        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES);

        assertThat(map.ringEffects()).isEqualTo(RingEffectTuning.DEFAULT);
    }

    @Test
    void theEightArgumentConstructorKeepsTheTuningItIsGiven() {
        RingEffectTuning tuning = new RingEffectTuning(2.0, 0.25);

        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES, tuning);

        assertThat(map.ringEffects()).isEqualTo(tuning);
    }

    @Test
    void keepsWhatItWasGiven() {
        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES);

        assertThat(map.key().asString()).isEqualTo("elytrarace:nether-sprint");
        assertThat(map.world()).isEqualTo("world_nether");
        assertThat(map.spawn()).isEqualTo(new Vec3(0.5, 70.0, -12.25));
        assertThat(map.rings()).containsExactlyElementsOf(RINGS);
        assertThat(map.referenceTime()).isEqualTo(Duration.ofSeconds(45));
        assertThat(map.boostConfig()).isEqualTo(BOOST);
        assertThat(map.guideLine()).isEqualTo(NO_GUIDES);
    }

    @Test
    void aMapWithoutADisplayNameDisplaysItsId() {
        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES);

        assertThat(map.displayName()).isEqualTo("elytrarace:nether-sprint");
    }

    @Test
    void aBlankDisplayNameDisplaysTheId() {
        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES,
                RingEffectTuning.DEFAULT, "");

        assertThat(map.displayName()).isEqualTo("elytrarace:nether-sprint");
    }

    @Test
    void keepsTheDisplayNameItIsGiven() {
        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES,
                RingEffectTuning.DEFAULT, "Blue & Red");

        assertThat(map.displayName()).isEqualTo("Blue & Red");
        assertThat(map.key().asString()).isEqualTo("elytrarace:nether-sprint");
    }

    @Test
    void rejectsRingsWhoseIndicesAreOutOfOrder() {
        // A loader that sorts by file order rather than by index would produce exactly this, and a
        // tracker testing rings.get(passedCount) would then demand them in the wrong sequence.
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO,
                List.of(ring(1, 100.0), ring(0, 200.0)), Duration.ofSeconds(45), BOOST, NO_GUIDES))
                .isInstanceOf(InvalidMapException.class);
    }

    @Test
    void rejectsAnEmptyRingList() {
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO, List.of(),
                Duration.ofSeconds(45), BOOST, NO_GUIDES))
                .isInstanceOf(InvalidMapException.class);
    }

    @Test
    void rejectsANonPositiveReferenceTime() {
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO, RINGS, Duration.ZERO, BOOST, NO_GUIDES))
                .isInstanceOf(InvalidMapException.class);
    }

    @Test
    void rejectsABlankNameOrWorld() {
        assertThatThrownBy(() -> new MapDefinition("  ", "w", Vec3.ZERO, RINGS,
                Duration.ofSeconds(45), BOOST, NO_GUIDES))
                .isInstanceOf(InvalidMapException.class);
        assertThatThrownBy(() -> new MapDefinition("m", "", Vec3.ZERO, RINGS,
                Duration.ofSeconds(45), BOOST, NO_GUIDES))
                .isInstanceOf(InvalidMapException.class);
    }

    /**
     * A guide point bends the line between two rings, so it has to have two rings to sit between. With
     * two rings the course spans order indices 0 to 100, and 150 is past its end: a line reaching it
     * would run off the far side of the last ring rather than bend anything.
     */
    @Test
    void rejectsAGuidePointPastTheLastRing() {
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO, RINGS, Duration.ofSeconds(45), BOOST,
                new GuideLine(List.of(new GuidePoint(150, new Vec3(4.0, 64.0, 250.0))), 3, 1.75)))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("outside 0..100");
    }

    /** And the same in front of the first ring, which integer division would report as ring 0. */
    @Test
    void rejectsAGuidePointInFrontOfTheFirstRing() {
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO, RINGS, Duration.ofSeconds(45), BOOST,
                new GuideLine(List.of(new GuidePoint(-50, new Vec3(4.0, 64.0, 50.0))), 3, 1.75)))
                .isInstanceOf(InvalidMapException.class)
                .hasMessageContaining("outside 0..100");
    }

    @Test
    void keepsAGuidePointThatSitsBetweenTwoOfItsRings() {
        GuidePoint guide = new GuidePoint(50, new Vec3(4.0, 64.0, 150.0));

        MapDefinition map = new MapDefinition("m", "w", Vec3.ZERO, RINGS, Duration.ofSeconds(45), BOOST,
                new GuideLine(List.of(guide), 3, 1.75));

        assertThat(map.guideLine().points()).containsExactly(guide);
    }

    @Test
    void copiesItsRingListSoACallerCannotChangeItAfterwards() {
        List<Ring> mutable = new java.util.ArrayList<>(RINGS);
        MapDefinition map = new MapDefinition("m", "w", Vec3.ZERO, mutable, Duration.ofSeconds(45), BOOST, NO_GUIDES);

        mutable.clear();

        assertThat(map.rings()).hasSize(2);
    }

    @Test
    void aMapBuiltWithAKeyReturnsThatKey() {
        MapDefinition map = new MapDefinition(CatalogKeys.of("blue_and_red"), "ElytraraceBlueAndRed",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES,
                RingEffectTuning.DEFAULT, "Blue & Red", List.of());

        assertThat(map.key().asString()).isEqualTo("elytrarace:blue_and_red");
        assertThat(map.key().namespace()).isEqualTo("elytrarace");
        assertThat(map.world()).isEqualTo("ElytraraceBlueAndRed");
    }

    @Test
    void aMapWithoutAuthorsHasNone() {
        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES);

        assertThat(map.authors()).isEmpty();
    }

    @Test
    void aMapKeepsItsAuthors() {
        Author author = new Author(java.util.UUID.fromString("05bf52c6-7bb0-4f13-8951-0e1fd803df35"), "TheMeinerLP");

        MapDefinition map = new MapDefinition(CatalogKeys.of("blue_and_red"), "ElytraraceBlueAndRed",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES,
                RingEffectTuning.DEFAULT, null, List.of(author));

        assertThat(map.authors()).containsExactly(author);
    }

    @Test
    void aMapKeyInAnotherNamespaceIsRefused() {
        assertThatThrownBy(() -> new MapDefinition(net.kyori.adventure.key.Key.key("other", "blue_and_red"),
                "w", new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST, NO_GUIDES,
                RingEffectTuning.DEFAULT, null, List.of()))
                .isInstanceOf(InvalidCatalogKeyException.class)
                .hasMessageContaining("key");
    }
}
