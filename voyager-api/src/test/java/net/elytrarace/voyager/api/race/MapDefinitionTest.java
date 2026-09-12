package net.elytrarace.voyager.api.race;

import net.elytrarace.voyager.api.math.Vec3;
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

    @Test
    void keepsWhatItWasGiven() {
        MapDefinition map = new MapDefinition("nether-sprint", "world_nether",
                new Vec3(0.5, 70.0, -12.25), RINGS, Duration.ofSeconds(45), BOOST);

        assertThat(map.name()).isEqualTo("nether-sprint");
        assertThat(map.world()).isEqualTo("world_nether");
        assertThat(map.spawn()).isEqualTo(new Vec3(0.5, 70.0, -12.25));
        assertThat(map.rings()).containsExactlyElementsOf(RINGS);
        assertThat(map.referenceTime()).isEqualTo(Duration.ofSeconds(45));
        assertThat(map.boostConfig()).isEqualTo(BOOST);
    }

    @Test
    void rejectsRingsWhoseIndicesAreOutOfOrder() {
        // A loader that sorts by file order rather than by index would produce exactly this, and a
        // tracker testing rings.get(passedCount) would then demand them in the wrong sequence.
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO,
                List.of(ring(1, 100.0), ring(0, 200.0)), Duration.ofSeconds(45), BOOST))
                .isInstanceOf(InvalidMapException.class);
    }

    @Test
    void rejectsAnEmptyRingList() {
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO, List.of(), Duration.ofSeconds(45), BOOST))
                .isInstanceOf(InvalidMapException.class);
    }

    @Test
    void rejectsANonPositiveReferenceTime() {
        assertThatThrownBy(() -> new MapDefinition("m", "w", Vec3.ZERO, RINGS, Duration.ZERO, BOOST))
                .isInstanceOf(InvalidMapException.class);
    }

    @Test
    void rejectsABlankNameOrWorld() {
        assertThatThrownBy(() -> new MapDefinition("  ", "w", Vec3.ZERO, RINGS, Duration.ofSeconds(45), BOOST))
                .isInstanceOf(InvalidMapException.class);
        assertThatThrownBy(() -> new MapDefinition("m", "", Vec3.ZERO, RINGS, Duration.ofSeconds(45), BOOST))
                .isInstanceOf(InvalidMapException.class);
    }

    @Test
    void copiesItsRingListSoACallerCannotChangeItAfterwards() {
        List<Ring> mutable = new java.util.ArrayList<>(RINGS);
        MapDefinition map = new MapDefinition("m", "w", Vec3.ZERO, mutable, Duration.ofSeconds(45), BOOST);

        mutable.clear();

        assertThat(map.rings()).hasSize(2);
    }
}
