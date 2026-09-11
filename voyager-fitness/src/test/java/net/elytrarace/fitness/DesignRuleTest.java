package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClass;
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
    private static final ArchCondition<JavaClass> BE_A_RECORD_AN_INTERFACE_OR_AN_ENUM =
            new ArchCondition<>("be a record, an interface or an enum") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    if (!item.isRecord() && !item.isInterface() && !item.isEnum()) {
                        events.add(SimpleConditionEvent.violated(item,
                                "%s is neither a record, an interface nor an enum".formatted(item.getName())));
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
                    .should(BE_A_RECORD_AN_INTERFACE_OR_AN_ENUM)
                    .because("voyager-api carries interfaces, records, enums and exceptions only")
                    .allowEmptyShould(false);
}
