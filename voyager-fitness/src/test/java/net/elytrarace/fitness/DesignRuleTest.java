package net.elytrarace.fitness;

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

@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class DesignRuleTest {

    // ArchUnit 1.4.2 has beInterfaces() and beEnums() but no beRecords(), so the record case is
    // expressed through JavaClass.isRecord() directly.
    //
    // Rule 1 sanctions exactly one class shape here: the "non-sealed abstract class BaseX" extension
    // point a sealed domain interface permits (RingEffect permits BaseRingEffect, see the greenfield
    // design's "Design rules, concretely" list). That class carries no I/O and no platform import —
    // it is the closed hierarchy's hook, not an implementation — so it stays in voyager-api rather
    // than moving to voyager-race. Matched narrowly: abstract AND named Base*, not abstract alone,
    // so an unrelated abstract class can't sneak through the same door.
    private static final ArchCondition<JavaClass> BE_A_RECORD_AN_INTERFACE_AN_ENUM_OR_A_SEALED_BASE =
            new ArchCondition<>(
                    "be a record, an interface, an enum, or a sealed hierarchy's abstract Base* extension point") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    boolean isSealedExtensionPoint = item.getModifiers().contains(JavaModifier.ABSTRACT)
                            && item.getSimpleName().startsWith("Base");
                    if (!item.isRecord() && !item.isInterface() && !item.isEnum() && !isSealedExtensionPoint) {
                        events.add(SimpleConditionEvent.violated(item,
                                "%s is neither a record, an interface, an enum, nor an abstract Base* "
                                        + "extension point".formatted(item.getName())));
                    }
                }
            };

    @ArchTest
    static final ArchRule exceptionsAreUncheckedAndDomainNamed =
            classes().that().haveSimpleNameEndingWith("Exception").and().areNotInterfaces()
                    .should().beAssignableTo(RuntimeException.class)
                    .because("rule 9 — domain exceptions are unchecked")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule exceptionsLiveInAnExceptionSubpackage =
            classes().that().resideInAPackage("net.elytrarace.voyager..")
                    .and().haveSimpleNameEndingWith("Exception")
                    .should().resideInAPackage("..exception..")
                    .because("rule 9 — an exception sits in an exception subpackage beside the domain "
                            + "that throws it, never in one repo-wide collection package")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiTypesAreRecordsInterfacesOrEnums =
            classes().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .and().areTopLevelClasses()
                    .and().haveSimpleNameNotEndingWith("Exception")
                    .and().haveSimpleNameNotEndingWith("package-info")
                    .should(BE_A_RECORD_AN_INTERFACE_AN_ENUM_OR_A_SEALED_BASE)
                    .because("voyager-api carries interfaces, records, enums and exceptions, plus rule "
                            + "1's non-sealed abstract Base* extension point for a sealed domain interface")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule race_domain_exceptions_are_runtime_exceptions =
            classes().that().resideInAnyPackage("net.elytrarace.voyager.race..", "net.elytrarace.voyager.api.race..")
                    .and().haveSimpleNameEndingWith("Exception")
                    .should().beAssignableTo(RuntimeException.class)
                    .as("domain exceptions in net.elytrarace.voyager.race.. or net.elytrarace.voyager.api.race.. "
                            + "extend RuntimeException")
                    .allowEmptyShould(false);
}
