package net.elytrarace.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.elytrarace.common.ecs.Component;
import net.elytrarace.common.ecs.System;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture rules enforcing ECS naming and structural conventions inspired
 * by the ManisGame reference design.
 *
 * <p>Every rule is evaluated against production classes only
 * ({@link ImportOption.DoNotIncludeTests}).
 */
@AnalyzeClasses(packages = "net.elytrarace", importOptions = ImportOption.DoNotIncludeTests.class)
class EcsArchitectureTest {

    @ArchTest
    static final ArchRule components_must_be_named_component =
            classes().that().implement(Component.class)
                    .should().haveSimpleNameEndingWith("Component")
                    .allowEmptyShould(true)
                    .because("ECS data holders must have the *Component suffix (ManisGame convention)");

    @ArchTest
    static final ArchRule systems_must_be_named_system =
            classes().that().implement(System.class)
                    .should().haveSimpleNameEndingWith("System")
                    .allowEmptyShould(true)
                    .because("ECS processors must have the *System suffix (ManisGame convention)");

    @ArchTest
    static final ArchRule systems_must_reside_in_system_package =
            classes().that().implement(System.class)
                    .should().resideInAPackage("..system..")
                    .allowEmptyShould(true)
                    .because("Systems belong in a dedicated system subpackage");

    @ArchTest
    static final ArchRule components_must_implement_component_interface =
            classes().that().haveSimpleNameEndingWith("Component")
                    .and().areNotInterfaces()
                    .and().areNotAnnotations()
                    .and().resideInAPackage("net.elytrarace.server.ecs.component..")
                    .should().implement(Component.class)
                    .allowEmptyShould(true)
                    .because("Every *Component in the ECS component package must implement the Component marker interface");
}
