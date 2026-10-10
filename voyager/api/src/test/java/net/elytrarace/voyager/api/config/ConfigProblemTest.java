package net.elytrarace.voyager.api.config;

import net.elytrarace.voyager.api.config.ConfigProblem.Severity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigProblemTest {

    @Test
    void refusesABlankKeyNamingTheField() {
        assertThatThrownBy(() -> new ConfigProblem(" ", "/data/maps/a.json", "bad", Severity.ERROR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("key must not be blank");
    }

    @Test
    void refusesABlankSourceNamingTheField() {
        assertThatThrownBy(() -> new ConfigProblem("world", "", "bad", Severity.ERROR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source must not be blank");
    }

    @Test
    void refusesANullSeverityNamingTheField() {
        assertThatThrownBy(() -> new ConfigProblem("world", "/data/maps/a.json", "bad", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("severity must not be null");
    }

    @Test
    void formatsSeveritySourceKeyAndMessageOnOneLine() {
        ConfigProblem problem = new ConfigProblem("world", "/data/maps/a.json",
                "world 'x' holds no region data", Severity.ERROR);

        assertThat(problem.format())
                .isEqualTo("ERROR /data/maps/a.json world: world 'x' holds no region data");
    }

    @Test
    void sortsBySourceThenKeyAndKeepsEqualPairsInTheOrderGiven() {
        ConfigProblem laterSourceEarlyKey = new ConfigProblem("a", "/z.json", "first", Severity.ERROR);
        ConfigProblem sameSourceLaterKey = new ConfigProblem("world", "/m.json", "second", Severity.ERROR);
        ConfigProblem sameSourceEarlyKey = new ConfigProblem("name", "/m.json", "third", Severity.ERROR);
        ConfigProblem sameSourceEarlyKeyAgain = new ConfigProblem("name", "/m.json", "fourth", Severity.WARNING);

        List<ConfigProblem> problems = new ArrayList<>(List.of(
                laterSourceEarlyKey, sameSourceLaterKey, sameSourceEarlyKey, sameSourceEarlyKeyAgain));
        problems.sort(ConfigProblem.ORDER);

        assertThat(problems).containsExactly(
                sameSourceEarlyKey, sameSourceEarlyKeyAgain, sameSourceLaterKey, laterSourceEarlyKey);
    }
}
