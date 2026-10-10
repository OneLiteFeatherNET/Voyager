package net.elytrarace.fitness;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.server.CatalogReloadService;
import net.elytrarace.voyager.server.command.RaceCommand;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The rules of the server's composition root (the dependency-injection-boundaries capability of the change
 * extract-cup-slice). The race command is built by a bean of {@code server.inject}, and by nothing else.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class CompositionRootRulesTest {

    @ArchTest
    static final ArchRule raceCommandIsConstructedOnlyByTheCompositionRoot =
            noClasses().that().resideOutsideOfPackage("net.elytrarace.voyager.server.inject..")
                    .should().callConstructor(RaceCommand.class, CupSession.class, boolean.class,
                            CatalogReloadService.class)
                    .as("classes outside net.elytrarace.voyager.server.inject.. do not call a RaceCommand constructor")
                    .allowEmptyShould(false);
}
