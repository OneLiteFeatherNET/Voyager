package net.elytrarace.fitness;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * CloudNet is a runtime platform the node provides, not a library the rebuild owns. These rules keep every
 * {@code eu.cloudnetservice} reference inside {@code voyager-cloudnet-bridge}, and keep the two composition roots and
 * the platform free of it, so that the fat jars can never carry a CloudNet class (ADR-0026, design decision 4).
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class CloudNetBoundaryTest {

    private static final String CLOUDNET = "eu.cloudnetservice..";
    private static final String BRIDGE = "net.elytrarace.voyager.cloudnet.bridge..";
    private static final String HANDOFF = "net.elytrarace.voyager.api.handoff..";

    @ArchTest
    static final ArchRule cloudNetIsConfinedToBridge =
            noClasses().that().resideOutsideOfPackage(BRIDGE)
                    .should().dependOnClassesThat().resideInAPackage(CLOUDNET)
                    .because("only voyager-cloudnet-bridge may name a CloudNet type; every other module reaches CloudNet "
                            + "only through the Minestom extension loaded from the node's extensions/ folder")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule rootsAndPlatformDoNotDependOnCloudNet =
            noClasses().that().resideInAnyPackage("..server..", "..setup..", "..platform..", "..api..")
                    .should().dependOnClassesThat().resideInAnyPackage(CLOUDNET, "net.elytrarace.voyager.cloudnet..")
                    .because("the composition roots and the platform are shipped in fat jars, and a CloudNet class in one of "
                            + "them would load a second CloudNet beside the node's")
                    .allowEmptyShould(false);

    /**
     * The bridge is loaded beside the node's CloudNet and names almost no Voyager type: every Voyager type it could name
     * would be a second copy in the extension's class loader. Its dependencies are the JDK, Minestom, Adventure, CloudNet,
     * the annotations and its own package, and the one Voyager package the hub handoff needs, {@code api.handoff}
     * (ADR-0026, amended 2026-10-13; ADR-0033). The handoff port is the only Voyager type the bridge may name.
     */
    @ArchTest
    static final ArchRule bridgeDependsOnlyOnJdkAndLibraries =
            classes().that().resideInAPackage(BRIDGE)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "java..", "net.minestom..", "net.kyori..", CLOUDNET, "org.jetbrains..", BRIDGE, HANDOFF)
                    .because("voyager-cloudnet-bridge loads in CloudNet's extension class loader and may name only the hub "
                            + "handoff port of voyager-api among Voyager types; it answers permission questions through the "
                            + "pointer that the platform installs on each player")
                    .allowEmptyShould(false);
}
