package net.elytrarace.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architecture rules enforcing naming and structural conventions across the
 * Voyager codebase (ManisGame reference design).
 *
 * <ul>
 *   <li>Factory utility classes must declare a private constructor.</li>
 *   <li>*ServiceImpl classes must implement a corresponding *Service interface.</li>
 *   <li>Custom exceptions must extend {@link RuntimeException} (unchecked).</li>
 *   <li>Default* concrete classes must be declared {@code final}.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "net.elytrarace", importOptions = ImportOption.DoNotIncludeTests.class)
class NamingConventionTest {

    // -------------------------------------------------------------------------
    // Factory classes
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule factories_must_have_private_constructor =
            classes().that().haveSimpleNameEndingWith("Factory")
                    .and().areNotInterfaces()
                    .should().haveOnlyPrivateConstructors()
                    .allowEmptyShould(true)
                    .because("Factory utility classes must not be instantiated; use a private constructor (ManisGame convention)");

    // -------------------------------------------------------------------------
    // Service interface + implementation pairing
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule service_impls_must_implement_service_interface =
            classes().that().haveSimpleNameEndingWith("ServiceImpl")
                    .should(implementAServiceInterface())
                    .because("*ServiceImpl must implement a corresponding *Service interface (interface+impl pattern)");

    /**
     * Custom condition: the class (or any of its super-types) must directly or
     * transitively implement at least one interface whose simple name ends with
     * "Service".
     */
    private static ArchCondition<JavaClass> implementAServiceInterface() {
        return new ArchCondition<>("implement a corresponding *Service interface") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                // getAllRawInterfaces() returns the transitive closure of all
                // interfaces implemented by the class and its super-types.
                boolean implementsService = javaClass.getAllRawInterfaces().stream()
                        .anyMatch(iface -> iface.getSimpleName().endsWith("Service"));

                if (!implementsService) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " does not implement any *Service interface"));
                }
            }
        };
    }

    // -------------------------------------------------------------------------
    // Exception conventions
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule exceptions_must_extend_runtime_exception =
            classes().that().haveSimpleNameEndingWith("Exception")
                    .and().areNotInterfaces()
                    .should().beAssignableTo(RuntimeException.class)
                    .because("All custom exceptions must extend RuntimeException for unchecked error handling");

    // -------------------------------------------------------------------------
    // Default implementations
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule default_providers_must_be_final =
            classes().that().haveSimpleNameStartingWith("Default")
                    .and().areNotInterfaces()
                    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                    .should().haveModifier(JavaModifier.FINAL)
                    .allowEmptyShould(true)
                    .because("Default implementations of provider/registry interfaces must be final (ManisGame convention)");
}
