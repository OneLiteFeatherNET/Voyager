package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import net.elytrarace.voyager.platform.cup.CupSession;
import net.elytrarace.voyager.server.CatalogReloadService;
import net.elytrarace.voyager.server.command.RaceCommand;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

/**
 * The rules of the server's composition root (the dependency-injection-boundaries capability of the change
 * extract-cup-slice). The race command is built by a bean of {@code server.inject}, and by nothing else.
 *
 * <p>The lobby settings are read in one place: {@code server.config.ServerSettings} alone declares the constants that
 * name {@code VOYAGER_LOBBY_WORLD}, {@code VOYAGER_LOBBY_SPAWN} and {@code VOYAGER_MAX_PLAYERS}. The rule is by name,
 * because a compiler inlines a constant string into every class that reads it, so a rule over the classes cannot see a
 * read of the literal. What it can see is a second declaration of one of the setting constants, outside the config
 * package, which is how the names drift apart.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class CompositionRootRulesTest {

    /** The names of the constants {@code server.config.ServerSettings} alone declares for the lobby settings. */
    private static final Set<String> LOBBY_SETTING_CONSTANTS =
            Set.of("LOBBY_WORLD_PROPERTY", "LOBBY_SPAWN_PROPERTY", "MAX_PLAYERS_PROPERTY");

    @ArchTest
    static final ArchRule raceCommandIsConstructedOnlyByTheCompositionRoot =
            noClasses().that().resideOutsideOfPackage("net.elytrarace.voyager.server.inject..")
                    .should().callConstructor(RaceCommand.class, CupSession.class, boolean.class,
                            CatalogReloadService.class)
                    .as("classes outside net.elytrarace.voyager.server.inject.. do not call a RaceCommand constructor")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule onlyServerConfigReadsLobbySettings =
            fields().that().areDeclaredInClassesThat().resideOutsideOfPackage("net.elytrarace.voyager.server.config..")
                    .should(notDeclareALobbySettingConstant())
                    .as("classes outside net.elytrarace.voyager.server.config.. do not declare the lobby setting "
                            + "constants: only ServerSettings names VOYAGER_LOBBY_WORLD, VOYAGER_LOBBY_SPAWN and "
                            + "VOYAGER_MAX_PLAYERS")
                    .allowEmptyShould(false);

    /** A positive condition: a field declared outside the config package is a violation when it has a setting's name. */
    private static ArchCondition<JavaField> notDeclareALobbySettingConstant() {
        return new ArchCondition<>("not declare a lobby setting constant") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                if (LOBBY_SETTING_CONSTANTS.contains(field.getName())) {
                    events.add(SimpleConditionEvent.violated(field,
                            "%s declares the lobby setting constant %s".formatted(
                                    field.getFullName(), field.getName())));
                } else {
                    events.add(SimpleConditionEvent.satisfied(field, field.getFullName()));
                }
            }
        };
    }
}
