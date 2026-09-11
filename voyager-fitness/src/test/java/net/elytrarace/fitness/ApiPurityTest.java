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
}
