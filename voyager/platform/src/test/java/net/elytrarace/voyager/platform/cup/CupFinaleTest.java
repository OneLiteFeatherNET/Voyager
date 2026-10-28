package net.elytrarace.voyager.platform.cup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CupFinaleTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID FOURTH = UUID.fromString("00000000-0000-0000-0000-000000000004");

    /** Drives the finale for its whole length, one tick at a time, and returns every burst it launched. */
    private static List<CupFinale.Burst> runToTheEnd(CupFinale finale) {
        List<CupFinale.Burst> launched = new ArrayList<>();
        while (!finale.over()) {
            launched.addAll(finale.tick());
        }
        return launched;
    }

    private static long burstsOf(List<CupFinale.Burst> launched, UUID racer) {
        return launched.stream().filter(burst -> burst.racer().equals(racer)).count();
    }

    @Test
    void theWinnerGetsFiveBursts() {
        CupFinale finale = new CupFinale(Map.of(FIRST, 1));

        assertThat(burstsOf(runToTheEnd(new CupFinale(Map.of(FIRST, 1))), FIRST)).isEqualTo(5);
        assertThat(finale.over()).isFalse();
    }

    @Test
    void theSecondAndThirdPlacesGetTwoBurstsEach() {
        List<CupFinale.Burst> launched = runToTheEnd(new CupFinale(Map.of(SECOND, 2, THIRD, 3)));

        assertThat(burstsOf(launched, SECOND)).isEqualTo(2);
        assertThat(burstsOf(launched, THIRD)).isEqualTo(2);
    }

    @Test
    void aPlaceBelowThreeGetsNoBurst() {
        List<CupFinale.Burst> launched = runToTheEnd(new CupFinale(Map.of(FOURTH, 4)));

        assertThat(launched).isEmpty();
    }

    @Test
    void everyBurstIsLaunchedWithinEightyTicksOfTheCupEnding() {
        List<CupFinale.Burst> launched = new ArrayList<>();
        CupFinale finale = new CupFinale(Map.of(FIRST, 1, SECOND, 2, THIRD, 3));
        int tick = 0;
        while (!finale.over()) {
            List<CupFinale.Burst> due = finale.tick();
            if (!due.isEmpty()) {
                assertThat(tick).isLessThan(CupFinale.LENGTH_TICKS);
            }
            launched.addAll(due);
            tick++;
        }

        assertThat(launched).hasSize(5 + 2 + 2);
        assertThat(CupFinale.LENGTH_TICKS).isEqualTo(80);
    }

    @Test
    void theWinnersBurstsAreSpreadNotAllAtOnce() {
        CupFinale finale = new CupFinale(Map.of(FIRST, 1));
        int distinctTicks = 0;
        while (!finale.over()) {
            if (!finale.tick().isEmpty()) {
                distinctTicks++;
            }
        }

        assertThat(distinctTicks).isGreaterThan(1);
    }
}
