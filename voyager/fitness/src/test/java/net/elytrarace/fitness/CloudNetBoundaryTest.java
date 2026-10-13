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
     * The bridge is loaded beside the node's CloudNet and knows nothing of Voyager: it reads the pointer that a player
     * carries, and every Voyager type it could name would be a second copy in the extension's class loader. Its
     * dependencies are the JDK, Minestom, Adventure, CloudNet and the annotations, and its own package.
     */
    @ArchTest
    static final ArchRule bridgeDependsOnlyOnJdkAndLibraries =
            classes().that().resideInAPackage(BRIDGE)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "java..", "net.minestom..", "net.kyori..", CLOUDNET, "org.jetbrains..", BRIDGE)
                    .because("voyager-cloudnet-bridge loads in CloudNet's extension class loader and must not name a Voyager "
                            + "type; it answers through the pointer that the platform installs on each player")
                    .allowEmptyShould(false);
}
