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
@AnalyzeClasses(packages = "net.elytrarace", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiPurityTest {

    @ArchTest
    static final ArchRule apiDoesNotDependOnMinestom =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.minestom..")
                    .because("Minestom types belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("ports live in the api module, ORM technology does not")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnADiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "jakarta.inject..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotPerformFileIo =
            noClasses().that().resideInAPackage("net.elytrarace.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("java.nio.file..")
                    .because("voyager-api declares configuration types and never loads them")
                    .allowEmptyShould(false);
}
