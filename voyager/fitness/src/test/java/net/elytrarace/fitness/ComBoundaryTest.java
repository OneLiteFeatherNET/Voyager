package net.elytrarace.fitness;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import net.elytrarace.voyager.server.config.ServerSettings;

import java.time.Instant;
import java.util.Random;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The computer racer boundary (design D9, F1 to F4). The rules are not frozen: the computer racer code starts clean,
 * so a violation fails the build rather than being recorded in {@code archunit_store}. Every rule uses
 * {@code allowEmptyShould(false)}, so a rule that matches no class fails instead of passing.
 *
 * <p>F5 (the slice cycle rules) is covered by {@code SliceBoundaryRulesTest} r4 and r7, which match {@code platform.com}
 * and {@code race.com} without a change.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class ComBoundaryTest {

    private static final String RACE_COM = "net.elytrarace.voyager.race.com..";
    private static final String PLATFORM_COM = "net.elytrarace.voyager.platform.com..";
    private static final String PLATFORM_CUP = "net.elytrarace.voyager.platform.cup..";
    private static final String SERVER_CONFIG = "net.elytrarace.voyager.server.config..";
    private static final String SERVER_INJECT = "net.elytrarace.voyager.server.inject..";

    /** F1: the brain is pure. No Minestom, no platform code, no wall clock, no unseeded randomness. */
    @ArchTest
    static final ArchRule comBrainIsPure =
            noClasses().that().resideInAPackage(RACE_COM)
                    .should().dependOnClassesThat().resideInAnyPackage("net.minestom..", "net.elytrarace.voyager.platform..")
                    .orShould().callMethod(System.class, "currentTimeMillis")
                    .orShould().callMethod(System.class, "nanoTime")
                    .orShould().callMethod(Instant.class, "now")
                    .orShould().callMethod(Math.class, "random")
                    .orShould().callConstructor(Random.class)
                    .because("the computer racer brain is a pure function of its state, tick and seed (design D1, F1): "
                            + "a wall-clock read or an unseeded Random makes a race unrepeatable")
                    .allowEmptyShould(false);

    /** F2: the cup sees computer racers only through the CompetitorField port, never through the Minestom adapter. */
    @ArchTest
    static final ArchRule cupDoesNotDependOnComPlatform =
            noClasses().that().resideInAPackage(PLATFORM_CUP)
                    .should().dependOnClassesThat().resideInAPackage(PLATFORM_COM)
                    .because("the cup owns the port (CompetitorField) and never learns that a computer racer is a Minestom "
                            + "entity (design D9, F2)")
                    .allowEmptyShould(false);

    /** F3: computer racers are never simulated by the flight model; the client stays authoritative for flight. */
    @ArchTest
    static final ArchRule comAdapterDoesNotSimulateFlight =
            noClasses().that().resideInAnyPackage(PLATFORM_COM, RACE_COM)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "net.elytrarace.voyager.physics..", "net.elytrarace.voyager.platform.flight..")
                    .because("a computer racer never feeds the flight tracker or the physics model: flight is client "
                            + "authoritative (CLAUDE.md, elytra velocity authority) and bots only move their own body "
                            + "(design D9, F3)")
                    .allowEmptyShould(false);

    /**
     * F4: the two computer racer settings are read only where the server reads its configuration. The rule names
     * the record accessors, not the property constants: javac inlines {@code public static final String} constants, so
     * a rule on {@code ServerSettings.COM_COUNT_PROPERTY} sees no bytecode reference and could never fail. The
     * accessors are the path every consumer takes to the value, and the composition root is the only caller.
     */
    @ArchTest
    static final ArchRule onlyServerConfigReadsComSettings =
            noClasses().that().resideOutsideOfPackages(SERVER_CONFIG, SERVER_INJECT)
                    .should().callMethod(ServerSettings.class, "comCount")
                    .orShould().callMethod(ServerSettings.class, "comDifficulty")
                    .because("the computer racer settings are read once, by server.config (ADR-0036, design D9, F4); "
                            + "a race or platform class that reads them bypasses the one precedence and the one source label")
                    .allowEmptyShould(false);
}
