package net.elytrarace.fitness;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.TextFileBasedViolationStore;

import java.util.Locale;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Slice-boundary rules of the rebuild (design D6 of the change freeze-slice-boundary-violations).
 *
 * <p>A rule with known violations on main is frozen: its violations are stored in the committed baseline under
 * {@code src/test/resources/archunit_store}, and only a violation that is not in the baseline fails the build. The
 * rule description is the key of its baseline entry, so it is written with {@code as(...)} and must stay stable.
 * R7 and R8 have no violations and are plain rules, so any violation fails them at once.
 *
 * <p>The composition root of the setup server (SetupServer and setup.inject) is wiring and is outside R6, as the
 * server's bootstrap is outside R2 (owner decision B, design D7).
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class SliceBoundaryRulesTest {

    @ArchTest
    static final ArchRule r1_serverDoesNotDependOnRaceScoring = freeze(
            noClasses().that().resideInAPackage("net.elytrarace.voyager.server..")
                    .should().dependOnClassesThat().resideInAPackage("net.elytrarace.voyager.race.scoring..")
                    .as("classes in net.elytrarace.voyager.server.. do not depend on net.elytrarace.voyager.race.scoring..")
                    .allowEmptyShould(false));

    @ArchTest
    static final ArchRule r2_serverGameDoesNotUseMinestom = freeze(
            noClasses().that().resideInAPackage("net.elytrarace.voyager.server.game..")
                    .should().dependOnClassesThat().resideInAPackage("net.minestom..")
                    .as("classes in net.elytrarace.voyager.server.game.. do not depend on net.minestom..")
                    .allowEmptyShould(false));

    @ArchTest
    static final ArchRule r3_platformInfrastructureDoesNotDependOnRace = freeze(
            noClasses().that().resideInAnyPackage(
                            "net.elytrarace.voyager.platform.text..",
                            "net.elytrarace.voyager.platform.convert..",
                            "net.elytrarace.voyager.platform.world..",
                            "net.elytrarace.voyager.platform.tick..",
                            "net.elytrarace.voyager.platform.render..")
                    .should().dependOnClassesThat().resideInAPackage("net.elytrarace.voyager.race..")
                    .as("classes in the platform infrastructure packages net.elytrarace.voyager.platform.text.., "
                            + "convert.., world.., tick.. and render.. do not depend on net.elytrarace.voyager.race..")
                    .allowEmptyShould(false));

    @ArchTest
    static final ArchRule r4_platformSlicesAreFreeOfCycles = freeze(
            slices().matching("net.elytrarace.voyager.platform.(*)..")
                    .should().beFreeOfCycles()
                    .as("slices matching net.elytrarace.voyager.platform.(*).. are free of cycles")
                    .allowEmptyShould(false));

    @ArchTest
    static final ArchRule r5_mapsetupContractsLiveInPlatformMapsetup = freeze(
            noClasses().that().resideInAPackage("net.elytrarace.voyager.platform..")
                    .and().resideOutsideOfPackage("net.elytrarace.voyager.platform.mapsetup..")
                    .should().dependOnClassesThat().resideInAPackage("net.elytrarace.voyager.api.mapsetup..")
                    .as("classes in net.elytrarace.voyager.platform.. that depend on net.elytrarace.voyager.api.mapsetup.. "
                            + "reside in net.elytrarace.voyager.platform.mapsetup..")
                    .allowEmptyShould(false));

    @ArchTest
    static final ArchRule r6_setupAdapterDoesNotUseMinestom = freeze(
            noClasses().that().resideInAPackage("net.elytrarace.voyager.setup..")
                    .and().resideOutsideOfPackage("net.elytrarace.voyager.setup.inject..")
                    .and().doNotHaveFullyQualifiedName("net.elytrarace.voyager.setup.SetupServer")
                    .should().dependOnClassesThat().resideInAPackage("net.minestom..")
                    .as("classes in net.elytrarace.voyager.setup.. outside the composition root "
                            + "(net.elytrarace.voyager.setup.SetupServer and net.elytrarace.voyager.setup.inject..) "
                            + "do not depend on net.minestom..")
                    .allowEmptyShould(false));

    @ArchTest
    static final ArchRule r7_raceSlicesAreFreeOfCycles =
            slices().matching("net.elytrarace.voyager.race.(*)..")
                    .should().beFreeOfCycles()
                    .as("slices matching net.elytrarace.voyager.race.(*).. are free of cycles")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule r8_apiSlicesAreFreeOfCycles =
            slices().matching("net.elytrarace.voyager.api.(*)..")
                    .should().beFreeOfCycles()
                    .as("slices matching net.elytrarace.voyager.api.(*).. are free of cycles")
                    .allowEmptyShould(false);

    /**
     * Freezes a rule with its known violations. The store file of a rule is named after its description, so a
     * diff of the store shows which rule changed.
     */
    private static FreezingArchRule freeze(ArchRule rule) {
        return FreezingArchRule.freeze(rule)
                .persistIn(new TextFileBasedViolationStore(SliceBoundaryRulesTest::storeFileName));
    }

    static String storeFileName(String ruleDescription) {
        String slug = ruleDescription.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug + ".txt";
    }
}
