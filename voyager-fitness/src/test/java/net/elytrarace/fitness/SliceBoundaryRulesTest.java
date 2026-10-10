package net.elytrarace.fitness;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Slice-boundary rules of the rebuild (design D6 of the change freeze-slice-boundary-violations).
 *
 * <p>The rules that have violations on main are not yet frozen: this is the Red step. Each rule's
 * description is written with {@code as(...)} and is the key of its baseline entry, so it must stay stable.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class SliceBoundaryRulesTest {

    @ArchTest
    static final ArchRule r1_serverDoesNotDependOnRaceScoring =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.server..")
                    .should().dependOnClassesThat().resideInAPackage("net.elytrarace.voyager.race.scoring..")
                    .as("classes in net.elytrarace.voyager.server.. do not depend on net.elytrarace.voyager.race.scoring..")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule r2_serverGameDoesNotUseMinestom =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.server.game..")
                    .should().dependOnClassesThat().resideInAPackage("net.minestom..")
                    .as("classes in net.elytrarace.voyager.server.game.. do not depend on net.minestom..")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule r3_platformInfrastructureDoesNotDependOnRace =
            noClasses().that().resideInAnyPackage(
                            "net.elytrarace.voyager.platform.text..",
                            "net.elytrarace.voyager.platform.convert..",
                            "net.elytrarace.voyager.platform.world..",
                            "net.elytrarace.voyager.platform.tick..",
                            "net.elytrarace.voyager.platform.render..")
                    .should().dependOnClassesThat().resideInAPackage("net.elytrarace.voyager.race..")
                    .as("classes in the platform infrastructure packages net.elytrarace.voyager.platform.text.., "
                            + "convert.., world.., tick.. and render.. do not depend on net.elytrarace.voyager.race..")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule r4_platformSlicesAreFreeOfCycles =
            slices().matching("net.elytrarace.voyager.platform.(*)..")
                    .should().beFreeOfCycles()
                    .as("slices matching net.elytrarace.voyager.platform.(*).. are free of cycles")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule r5_mapsetupContractsLiveInPlatformMapsetup =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.platform..")
                    .and().resideOutsideOfPackage("net.elytrarace.voyager.platform.mapsetup..")
                    .should().dependOnClassesThat().resideInAPackage("net.elytrarace.voyager.api.mapsetup..")
                    .as("classes in net.elytrarace.voyager.platform.. that depend on net.elytrarace.voyager.api.mapsetup.. "
                            + "reside in net.elytrarace.voyager.platform.mapsetup..")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule r6_setupDoesNotUseMinestom =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.setup..")
                    .should().dependOnClassesThat().resideInAPackage("net.minestom..")
                    .as("classes in net.elytrarace.voyager.setup.. do not depend on net.minestom..")
                    .allowEmptyShould(false);

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
}
