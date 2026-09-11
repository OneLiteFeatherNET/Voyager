package net.elytrarace.fitness;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * voyager-api is the module every other module depends on, so anything it drags in is dragged in
 * everywhere. These rules are the reason the dependency direction in the module graph holds.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiPurityTest {

    @ArchTest
    static final ArchRule apiDoesNotDependOnMinestom =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.minestom..")
                    .because("Minestom types belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("ports live in the api module, ORM technology does not")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnADiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "jakarta.inject..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotPerformFileIo =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("java.nio.file..")
                    .because("voyager-api declares configuration types and never loads them")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnMinestom =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.minestom..")
                    .because("Minestom types belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("the physics module simulates flight, ORM technology does not belong here")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnADiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "jakarta.inject..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotPerformFileIo =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("java.nio.file..")
                    .because("the physics module has no configuration of any kind and never loads files")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnRaceOrPlatform =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("net.elytrarace.voyager.race..", "net.elytrarace.voyager.platform..")
                    .because("physics is a pure simulation core that race and platform depend on, not vice versa")
                    .allowEmptyShould(false);

    // voyager-race is the module whose defining property is that a whole race runs without a server,
    // and until these three rules landed nothing enforced it: race appeared in this file only as a
    // forbidden target of physics, never as a subject. Xerus is named explicitly because the spec
    // names it ("Xerus is Minestom-bound and therefore may not appear in voyager-race") and because
    // E4 is the stage that first puts it on a neighbouring module's classpath, which is exactly when
    // an unenforced prohibition turns into an argument.
    @ArchTest
    static final ArchRule raceDoesNotDependOnMinestomOrXerus =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("net.minestom..", "net.theevilreaper.xerus..")
                    .because("a whole race has to play out in JUnit; Minestom and the Minestom-bound "
                            + "Xerus phase system belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule raceDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule raceDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("the race domain scores a run; storing one is voyager-persistence's job")
                    .allowEmptyShould(false);

    // E4 puts voyager-platform and voyager-server on this module's classpath for the first time.
    // They are the two modules where a stray import does the most damage, because they are the only
    // ones with a platform to reach for — so they land with purity rules already in place, not added
    // after the fact in the final review the way voyager-race's were in E3. The existing rules above
    // already forbid Minestom for voyager-api and voyager-physics, and forbid Minestom-and-Xerus for
    // voyager-race, and physicsDoesNotDependOnRaceOrPlatform already forbids voyager-platform for
    // voyager-physics — the six rules below close the remaining gaps: Xerus for voyager-api and
    // voyager-physics, voyager-platform for voyager-api and voyager-race, voyager-server for
    // voyager-platform, and the DI container for every module except voyager-server.

    @ArchTest
    static final ArchRule apiDoesNotDependOnXerus =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.theevilreaper.xerus..")
                    .because("Xerus is Minestom-bound and belongs to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPlatform =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.elytrarace.voyager.platform..")
                    .because("voyager-api is the module every other module depends on; a dependency "
                            + "back on voyager-platform would put a platform type into every module's graph")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnXerus =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.theevilreaper.xerus..")
                    .because("Xerus is Minestom-bound and belongs to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule raceDoesNotDependOnPlatform =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.elytrarace.voyager.platform..")
                    .because("a whole race has to play out in JUnit; voyager-platform depends on "
                            + "voyager-race, not the other way around")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule platformDoesNotDependOnServer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.platform..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.elytrarace.voyager.server..")
                    .because("voyager-server is the composition root that depends on voyager-platform, "
                            + "not the other way around")
                    .allowEmptyShould(false);

    // io.airlift:guice keeps upstream Guice's com.google.inject package name (see the greenfield
    // design, D10), so one rule scoped to that package plus io.airlift.. catches either artifact.
    // Written as "everything outside voyager-server" rather than naming each domain module
    // individually, so a future rebuild module is covered by construction instead of needing its own
    // DI-purity rule remembered on top.
    @ArchTest
    static final ArchRule onlyServerDependsOnDiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager..")
                    .and().resideOutsideOfPackage("net.elytrarace.voyager.server..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "io.airlift..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);
}
