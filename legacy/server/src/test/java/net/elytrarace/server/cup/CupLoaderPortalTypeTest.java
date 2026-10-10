package net.elytrarace.server.cup;

import net.elytrarace.common.cup.CupService;
import net.elytrarace.common.map.MapService;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.LocationDTO;
import net.elytrarace.common.map.model.PortalDTO;
import net.elytrarace.server.physics.RingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for issue #103: CupLoader must honour the portal type
 * instead of defaulting every ring to {@link RingType#STANDARD}.
 */
class CupLoaderPortalTypeTest {

    private static final List<LocationDTO> VALID_LOCATIONS = List.of(
            new LocationDTO(0, 64, 0, true),
            new LocationDTO(3, 64, 0, false),
            new LocationDTO(0, 64, 3, false)
    );

    private CupLoader loader;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        var cupService = CupService.create(tempDir);
        var mapService = MapService.create(tempDir);
        loader = new CupLoader(cupService, mapService, tempDir, tempDir);
    }

    @Test
    void standardPortalTypeMapsToStandardRing() {
        var portal = new FilePortalDTO(1, VALID_LOCATIONS, "STANDARD");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
    }

    @Test
    void boostPortalTypeMapsToBoostRing() {
        var portal = new FilePortalDTO(2, VALID_LOCATIONS, "BOOST");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.BOOST);
    }

    @Test
    void checkpointPortalTypeMapsToCheckpointRing() {
        var portal = new FilePortalDTO(3, VALID_LOCATIONS, "CHECKPOINT");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.CHECKPOINT);
    }

    @Test
    void bonusPortalTypeMapsToBonusRing() {
        var portal = new FilePortalDTO(4, VALID_LOCATIONS, "BONUS");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.BONUS);
    }

    @Test
    void slowPortalTypeMapsToSlowRing() {
        var portal = new FilePortalDTO(5, VALID_LOCATIONS, "SLOW");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.SLOW);
    }

    @Test
    void mixedPortalTypesProduceMixedRingTypes() {
        var portals = List.of(
                new FilePortalDTO(1, VALID_LOCATIONS, "STANDARD"),
                new FilePortalDTO(2, VALID_LOCATIONS, "BOOST"),
                new FilePortalDTO(3, VALID_LOCATIONS, "CHECKPOINT")
        );

        var rings = portals.stream().map(loader::convertPortalToRing).toList();

        assertThat(rings).extracting("type")
                .containsExactly(RingType.STANDARD, RingType.BOOST, RingType.CHECKPOINT);
    }

    @Test
    void nullPortalTypeFallsBackToStandard() {
        var portal = new FilePortalDTO(1, VALID_LOCATIONS, null);

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
    }

    @Test
    void blankPortalTypeFallsBackToStandard() {
        var portal = new FilePortalDTO(1, VALID_LOCATIONS, "   ");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
    }

    @Test
    void unknownPortalTypeFallsBackToStandard() {
        var portal = new FilePortalDTO(1, VALID_LOCATIONS, "TURBO_NITRO");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
    }

    @Test
    void lowercasePortalTypeIsNormalised() {
        var portal = new FilePortalDTO(1, VALID_LOCATIONS, "boost");

        var ring = loader.convertPortalToRing(portal);

        assertThat(ring.type()).isEqualTo(RingType.BOOST);
    }

    @Test
    void legacyPortalWithoutTypeFieldFallsBackToStandard() {
        PortalDTO legacyPortal = new FilePortalDTO(1, VALID_LOCATIONS);

        var ring = loader.convertPortalToRing(legacyPortal);

        assertThat(ring.type()).isEqualTo(RingType.STANDARD);
    }
}
