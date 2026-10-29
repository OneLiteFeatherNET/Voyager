package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Each message-boundary rule must fail on a seeded violation. A rule that matches nothing passes silently, so the rules
 * are proven to match: the seeded classes live in {@code net.elytrarace.voyager.seeded}, which the architecture run
 * never imports.
 */
class ApiPurityRulesSeededTest {

    private static final JavaClasses SEEDED = new ClassFileImporter().importPackages("net.elytrarace.voyager.seeded");

    private static final JavaClasses SEEDED_API = new ClassFileImporter().importPackages("net.elytrarace.voyager.api.seeded");

    @Test
    void anAdventureTypeOtherThanAKeyInTheApiIsCaught() {
        assertRuleCatches(ApiPurityTest.apiUsesAdventureOnlyForKeys, SEEDED_API);
    }

    @Test
    void aTranslationKeyNamedOutsideTheMessageLayerIsCaught() {
        assertRuleCatches(ApiPurityTest.onlyTheTextPackageNamesATranslationKey);
    }

    @Test
    void aMiniMessageSourceWrittenAtACallSiteIsCaught() {
        assertRuleCatches(ApiPurityTest.onlyTheTextPackageDeserializesMiniMessage);
    }

    @Test
    void aStringSentToACommandSenderIsCaught() {
        assertRuleCatches(ApiPurityTest.onlyTheTextPackageSendsAStringToACommandSender);
    }

    @Test
    void aRingShapeThatIsNotAFinalRecordIsCaught() {
        assertRuleCatches(DesignRuleTest.ringShapeIsSealedToCircleAndEllipse, SEEDED_API);
    }

    private static void assertRuleCatches(ArchRule rule) {
        assertRuleCatches(rule, SEEDED);
    }

    private static void assertRuleCatches(ArchRule rule, JavaClasses classes) {
        assertThatThrownBy(() -> rule.check(classes))
                .as("the rule must match its seeded violation, or it is vacuously true")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SeededViolations");
    }
}
