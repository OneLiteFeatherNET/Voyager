package net.elytrarace.fitness;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * LuckPerms is an optional runtime backend behind the PermissionPolicy port (ADR-0024). These rules keep it
 * confined to its adapter package, so that command and setup code can only ask the policy and never the backend.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class PermissionBoundaryTest {

    private static final String LUCKPERMS_ADAPTER = "net.elytrarace.voyager.platform.permission.luckperms..";

    /** Classes with at least one direct dependency on a LuckPerms or loader type, by package name. */
    private static final DescribedPredicate<JavaClass> DEPENDS_ON_LUCKPERMS =
            DescribedPredicate.describe("depend on net.luckperms.. or me.lucko..", javaClass ->
                    javaClass.getDirectDependenciesFromSelf().stream()
                            .map(dependency -> dependency.getTargetClass().getPackageName())
                            .anyMatch(name -> isInside(name, "net.luckperms") || isInside(name, "me.lucko")));

    @ArchTest
    static final ArchRule luckPermsIsConfinedToItsAdapter =
            classes().that(DEPENDS_ON_LUCKPERMS)
                    .should().resideInAPackage(LUCKPERMS_ADAPTER)
                    .because("only the LuckPerms adapter may name a LuckPerms type; every other class asks the "
                            + "PermissionPolicy port, so the backend can be replaced or absent without touching a "
                            + "command")
                    .allowEmptyShould(false);

    /**
     * Inside the adapter, only the gateway talks to the LuckPerms API. The policy and the bootstrap see the gateway
     * seam, so their tests run without LuckPerms on the class path.
     */
    @ArchTest
    static final ArchRule onlyTheGatewayNamesTheLuckPermsApi =
            noClasses().that().resideInAPackage(LUCKPERMS_ADAPTER)
                    .and().doNotHaveSimpleName("NetLuckPermsGateway")
                    .should().dependOnClassesThat().resideInAPackage("net.luckperms..")
                    .because("NetLuckPermsGateway is the one class that adapts LuckPerms; the policy works through "
                            + "the LuckPermsGateway seam so that its tests need no LuckPerms")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule commandsAskOnlyThePolicy =
            noClasses().that().resideInAnyPackage("..server.command..", "..setup.adapter..")
                    .should().dependOnClassesThat().resideInAPackage(LUCKPERMS_ADAPTER)
                    .because("command and setup adapters receive the PermissionPolicy bean from their composition "
                            + "root and must not choose or construct the LuckPerms backend themselves")
                    .allowEmptyShould(false);

    private static boolean isInside(String packageName, String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }
}
