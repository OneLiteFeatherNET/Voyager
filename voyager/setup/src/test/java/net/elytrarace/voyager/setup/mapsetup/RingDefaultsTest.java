package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.race.RingType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RingDefaultsTest {

    @Test
    void theDefaultRadiusIsTheRadiusOfEveryRingOfTheSampleMap() {
        assertThat(RingDefaults.RADIUS).isEqualTo(Math.sqrt(13));
        assertThat(RingDefaults.RADIUS).isEqualTo(3.605551275463989);
    }

    @Test
    void theDefaultRingIsStandardWithTenPoints() {
        assertThat(RingDefaults.POINTS).isEqualTo(10);
        assertThat(RingDefaults.TYPE).isEqualTo(RingType.STANDARD);
    }
}
