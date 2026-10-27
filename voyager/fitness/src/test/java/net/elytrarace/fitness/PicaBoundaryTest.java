package net.elytrarace.fitness;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The Pica boundary (ADR-0039): the dialog library is imported by the tutorial package and nothing else. A dialog is built
 * in one place, so a second caller cannot quietly add a second way to show one. The rule is not frozen: it starts clean.
 * It uses {@code allowEmptyShould(false)}, so a rule that matches no class fails instead of passing.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class PicaBoundaryTest {

    private static final String PICA = "net.onelitefeather.pica..";
    private static final String TUTORIAL = "net.elytrarace.voyager.platform.tutorial..";

    @ArchTest
    static final ArchRule onlyTheTutorialPackageImportsPica =
            noClasses().that().resideOutsideOfPackage(TUTORIAL)
                    .should().dependOnClassesThat().resideInAPackage(PICA)
                    .because("the tutorial book is the only dialog in Voyager, and Pica is confined to its package "
                            + "(ADR-0039), so the pages and their buttons are built in one place")
                    .allowEmptyShould(false);
}
