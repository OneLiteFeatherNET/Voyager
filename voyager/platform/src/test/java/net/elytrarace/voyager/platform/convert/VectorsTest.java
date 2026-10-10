package net.elytrarace.voyager.platform.convert;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.math.exception.NonFiniteVectorException;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorsTest {

    // Mutually distinct and not all positive: a factor cannot be told from a component swap on
    // (1, 1, 1), and a lost sign does not show up when every component is positive.
    private static final Vec3 DOMAIN_FIXTURE = new Vec3(0.75, -0.32, 1.6);
    private static final Vec MINESTOM_FIXTURE = new Vec(0.75, -0.32, 1.6);

    @Test
    void toMinestomProducesTheSameComponents() {
        Vec converted = Vectors.toMinestom(DOMAIN_FIXTURE);

        assertThat(converted.x()).isEqualTo(0.75);
        assertThat(converted.y()).isEqualTo(-0.32);
        assertThat(converted.z()).isEqualTo(1.6);
    }

    @Test
    void toDomainProducesTheSameComponents() {
        Vec3 converted = Vectors.toDomain(MINESTOM_FIXTURE);

        assertThat(converted.x()).isEqualTo(0.75);
        assertThat(converted.y()).isEqualTo(-0.32);
        assertThat(converted.z()).isEqualTo(1.6);
    }

    @Test
    void toMinestomThenToDomainRoundTripsExactly() {
        Point roundTripped = Vectors.toMinestom(DOMAIN_FIXTURE);
        Vec3 result = Vectors.toDomain(roundTripped);

        assertThat(result).isEqualTo(DOMAIN_FIXTURE);
    }

    @Test
    void toDomainThenToMinestomRoundTripsExactly() {
        Vec3 roundTripped = Vectors.toDomain(MINESTOM_FIXTURE);
        Vec result = Vectors.toMinestom(roundTripped);

        assertThat(result).isEqualTo(MINESTOM_FIXTURE);
    }

    // Vec3's compact constructor rejects a non-finite component, so it can never carry one to
    // toMinestom — only an incoming Minestom value can be non-finite, since it has never been
    // through that constructor. This is the boundary re-asserting finiteness on the way in.
    @Test
    void toDomainRejectsNonFiniteXFromMinestom() {
        Point nonFinite = new Vec(Double.NaN, 0.0, 0.0);

        assertThatThrownBy(() -> Vectors.toDomain(nonFinite))
                .isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void toDomainRejectsNonFiniteYFromMinestom() {
        Point nonFinite = new Vec(0.0, Double.POSITIVE_INFINITY, 0.0);

        assertThatThrownBy(() -> Vectors.toDomain(nonFinite))
                .isInstanceOf(NonFiniteVectorException.class);
    }

    @Test
    void toDomainRejectsNonFiniteZFromMinestom() {
        Point nonFinite = new Vec(0.0, 0.0, Double.NEGATIVE_INFINITY);

        assertThatThrownBy(() -> Vectors.toDomain(nonFinite))
                .isInstanceOf(NonFiniteVectorException.class);
    }
}
