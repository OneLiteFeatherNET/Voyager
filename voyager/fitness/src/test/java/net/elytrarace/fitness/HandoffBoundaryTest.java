package net.elytrarace.fitness;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.elytrarace.voyager.api.handoff.PlayerHandoff;
import net.elytrarace.voyager.api.handoff.PlayerHandoffs;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The hub handoff port is the one Voyager package the CloudNet bridge names (ADR-0033). The port itself stays pure: it depends
 * on the JDK, on voyager-api and on the annotations, and on nothing that does I/O. Only the bridge fills the slot.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class HandoffBoundaryTest {

    private static final String HANDOFF = "net.elytrarace.voyager.api.handoff..";
    private static final String BRIDGE = "net.elytrarace.voyager.cloudnet.bridge..";

    @ArchTest
    static final ArchRule handoffPortDependsOnlyOnJdkAndApi =
            classes().that().resideInAPackage(HANDOFF)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "java..", "net.elytrarace.voyager.api..", "org.jetbrains..")
                    .because("the handoff port is the one Voyager package the bridge names, so it must stay a pure port that "
                            + "the bridge and the server can both load")
                    .allowEmptyShould(false);

    /**
     * The server reads the slot and never fills it: the bridge installs the handoff when its extension starts. A server that
     * installed its own handoff would hide the bridge's, so the install is allowed in the bridge alone.
     */
    @ArchTest
    static final ArchRule onlyTheBridgeInstallsAPlayerHandoff =
            noClasses().that().resideOutsideOfPackage(BRIDGE)
                    .should().callMethod(PlayerHandoffs.class, "install", PlayerHandoff.class)
                    .because("only voyager-cloudnet-bridge installs a hub handoff; the server reads the slot and does not fill it")
                    .allowEmptyShould(false);
}
