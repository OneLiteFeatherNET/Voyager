package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bridge's dependency rule must fail on a seeded violation. A rule that matches nothing passes silently, so the rule is
 * proven to match: the seeded class lives in the bridge's package, under the test classes that the architecture run excludes.
 */
class CloudNetBoundaryRulesSeededTest {

    private static final JavaClasses SEEDED = new ClassFileImporter()
            .importPackages("net.elytrarace.voyager.cloudnet.bridge.seeded");

    @Test
    void aBridgeClassNamingARaceTypeIsCaught() {
        assertThatThrownBy(() -> CloudNetBoundaryTest.bridgeDependsOnlyOnJdkAndLibraries.check(SEEDED))
                .as("the rule must match its seeded violation, or it is vacuously true")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SeededBridgeViolations");
    }
}
