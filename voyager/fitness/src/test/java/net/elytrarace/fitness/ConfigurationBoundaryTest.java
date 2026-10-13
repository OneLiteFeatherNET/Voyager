package net.elytrarace.fitness;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Server configuration is read in one place (ADR-0036): {@code platform.config.ConfigLoader}, which the composition roots
 * build. These rules keep the environment, the system properties and avaje-config out of every other module. The rules are
 * plain ArchRules with no frozen store, so a new violation fails the build rather than being recorded.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class ConfigurationBoundaryTest {

    /** The calls that read configuration from the JVM: the environment and the system properties, and their typed readers. */
    private static final DescribedPredicate<JavaCall<?>> READS_THE_JVM_FOR_CONFIGURATION =
            JavaCall.Predicates.target(HasName.Predicates.name("getenv")
                            .or(HasName.Predicates.name("getProperty"))
                            .or(HasName.Predicates.name("getInteger"))
                            .or(HasName.Predicates.name("getLong"))
                            .or(HasName.Predicates.name("getBoolean")))
                    .and(JavaCall.Predicates.target(HasOwner.Predicates.With.owner(
                            JavaClass.Predicates.resideInAPackage("java.lang"))))
                    .as("read the environment or a system property");

    @ArchTest
    static final ArchRule environmentAccessIsConfinedToTheEdge =
            noClasses().that().resideOutsideOfPackages(
                            "net.elytrarace.voyager.platform.config..",
                            "net.elytrarace.voyager.server..",
                            "net.elytrarace.voyager.setup..")
                    .should().callMethodWhere(READS_THE_JVM_FOR_CONFIGURATION)
                    .because("configuration is read once, by platform.config.ConfigLoader, which the composition roots "
                            + "build (ADR-0036); a class that reads the environment or a system property itself "
                            + "bypasses the one precedence and the one source label")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule avajeConfigIsConfinedToTheEdge =
            noClasses().that().resideOutsideOfPackages(
                            "net.elytrarace.voyager.platform.config..",
                            "net.elytrarace.voyager.server..")
                    .should().dependOnClassesThat().resideInAPackage("io.avaje.config..")
                    .because("avaje-config is the loader's library (ADR-0036); nothing else in the rebuild imports it, so "
                            + "its static facade and its precedence rules cannot leak into a class that has no loader")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnConfigLibrary =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAPackage("io.avaje.config..")
                    .because("the API declares configuration types and never loads them (ADR-0036)")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnConfigLibrary =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAPackage("io.avaje.config..")
                    .because("the physics module simulates flight and has no configuration (ADR-0036)")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule raceDoesNotDependOnConfigLibrary =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat().resideInAPackage("io.avaje.config..")
                    .because("a whole race plays out without a server, so it is handed its settings and never reads them "
                            + "(ADR-0036)")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiPhysicsAndRaceDoNotReadTheEnvironmentOrProperties =
            noClasses().that().resideInAnyPackage(
                            "net.elytrarace.voyager.api..",
                            "net.elytrarace.voyager.physics..",
                            "net.elytrarace.voyager.race..")
                    .should().callMethodWhere(READS_THE_JVM_FOR_CONFIGURATION)
                    .because("the domain modules take their values as arguments; they do not read the JVM (ADR-0036)")
                    .allowEmptyShould(false);
}
