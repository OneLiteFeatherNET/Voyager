package net.elytrarace.voyager.race.flow;

import net.elytrarace.voyager.race.flow.StartGate.Situation;
import net.elytrarace.voyager.race.flow.StartGate.Verdict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The decision table of the waiting lobby, one row per situation and head count. The minimum is fixed at two in
 * the table; the clock never appears, because the platform reports the situation and this rule only decides.
 */
class StartGateTest {

    private static final int MINIMUM = 2;

    @ParameterizedTest(name = "{0} with {1} online and a minimum of 2 gives {2}")
    @CsvSource({
            "WAITING,    2, START_CUP",
            "WAITING,    1, HOLD",
            "COUNTDOWN,  1, CANCEL_COUNTDOWN",
            "COUNTDOWN,  2, HOLD",
            "COMMITTED,  0, ABORT_CUP",
            "COMMITTED,  1, HOLD",
            "RUNNING,    0, ABORT_CUP",
            "RUNNING,    1, HOLD"
    })
    void decidesTheVerdictForTheSituationAndTheHeadCount(Situation situation, int online, Verdict expected) {
        assertThat(StartGate.decide(situation, online, MINIMUM)).isEqualTo(expected);
    }

    @Test
    void rejectsAMinimumBelowOne() {
        assertThatThrownBy(() -> StartGate.decide(Situation.WAITING, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0");
    }

    @Test
    void rejectsANegativeHeadCount() {
        assertThatThrownBy(() -> StartGate.decide(Situation.WAITING, -1, MINIMUM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void commitWindowIsThreeSeconds() {
        assertThat(StartGate.COMMIT_WINDOW).isEqualTo(Duration.ofSeconds(3));
    }
}
