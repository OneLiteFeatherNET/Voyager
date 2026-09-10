package net.elytrarace.fitness;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture rules only constrain what is on the classpath. In the tree being replaced, rules were
 * declared for four modules that the test classpath never contained, so they passed without ever
 * evaluating anything. This test fails the build when a module is added and not wired in here.
 */
class FitnessCoverageTest {

    private static Set<String> property(String key) {
        return Arrays.stream(System.getProperty(key, "").split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toSet());
    }

    @Test
    void everyModuleOfTheRebuildIsOnTheFitnessClasspath() {
        Set<String> modules = property("voyager.allModules");
        Set<String> dependencies = property("voyager.fitnessDependencies");

        assertThat(modules).as("the build must supply voyager.allModules").isNotEmpty();
        assertThat(dependencies)
                .as("voyager-fitness must depend on every voyager-* module; add the missing ones to "
                        + "voyager-fitness/build.gradle.kts, then add their architecture rules")
                .containsExactlyInAnyOrderElementsOf(modules);
    }
}
