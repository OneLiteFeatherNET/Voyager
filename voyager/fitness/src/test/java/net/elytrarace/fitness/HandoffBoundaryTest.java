package net.elytrarace.fitness;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * The hub handoff port is the one Voyager package the CloudNet bridge names (ADR-0033). The port itself stays pure: it depends
 * on the JDK, on voyager-api and on the annotations, and on nothing that does I/O.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class HandoffBoundaryTest {

    private static final String HANDOFF = "net.elytrarace.voyager.api.handoff..";

    @ArchTest
    static final ArchRule handoffPortDependsOnlyOnJdkAndApi =
            classes().that().resideInAPackage(HANDOFF)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "java..", "net.elytrarace.voyager.api..", "org.jetbrains..")
                    .because("the handoff port is the one Voyager package the bridge names, so it must stay a pure port that "
                            + "the bridge and the server can both load")
                    .allowEmptyShould(false);
}
