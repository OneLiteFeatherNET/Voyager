package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The install rule must fail on a seeded install outside the bridge. A rule that matches nothing passes silently, so the rule is
 * proven to match: the seeded class lives in the server-side package, which the architecture run never imports.
 */
class HandoffRuleSeededTest {

    private static final JavaClasses SEEDED = new ClassFileImporter().importPackages("net.elytrarace.voyager.seeded");

    @Test
    void anInstallOutsideTheBridgeIsCaught() {
        assertThatThrownBy(() -> HandoffBoundaryTest.onlyTheBridgeInstallsAPlayerHandoff.check(SEEDED))
                .as("the rule must match its seeded install, or it is vacuously true")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SeededHandoffInstall");
    }
}
