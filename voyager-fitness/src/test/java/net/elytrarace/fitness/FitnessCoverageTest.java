package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture rules only constrain what is on the classpath, and only where a rule names it. In the
 * tree being replaced, rules were declared for four modules the test classpath never contained, so
 * they passed without evaluating anything. Checking module membership alone reproduces that defect
 * one level up: a single {@code testImplementation(project(...))} line would turn this green with no
 * rule constraining the module.
 *
 * <p>So for every module of the rebuild that carries production sources, this asserts both halves —
 * ArchUnit imported classes from it, and at least one declared {@code @ArchTest} rule names its
 * package prefix.
 */
class FitnessCoverageTest {

    /**
     * Hand-maintained on purpose. A new module has to be entered here deliberately, and the two
     * assertions below then refuse to pass until it is both on the fitness classpath and constrained
     * by a rule. {@code voyager-fitness} itself carries no production sources and is absent by
     * construction — it is not required to have rules about itself.
     */
    private static final Map<String, String> PACKAGE_PREFIX_BY_PROJECT = Map.of(
            ":voyager-api", "net.elytrarace.voyager.api");

    private static final String FITNESS_PACKAGE = "net.elytrarace.fitness";

    private static Set<String> property(String key) {
        return Arrays.stream(System.getProperty(key, "").split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toSet());
    }

    @Test
    void everyModuleWithProductionSourcesIsMappedToAPackagePrefix() {
        Set<String> modules = property("voyager.modulesWithSources");

        assertThat(modules)
                .as("the build must supply voyager.modulesWithSources; an empty list would pass vacuously")
                .isNotEmpty();
        assertThat(PACKAGE_PREFIX_BY_PROJECT.keySet())
                .as("every voyager-* module with production sources needs an entry in "
                        + "PACKAGE_PREFIX_BY_PROJECT, mapping its Gradle path to the package prefix its "
                        + "classes live in")
                .containsExactlyInAnyOrderElementsOf(modules);
    }

    @Test
    void everyMappedModuleIsImportedAndNamedByAtLeastOneRule() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(PACKAGE_PREFIX_BY_PROJECT.values());
        List<String> ruleDescriptions = declaredArchRuleDescriptions();

        assertThat(ruleDescriptions)
                .as("no @ArchTest rule was found in %s at all; the reflective scan below is broken, "
                        + "not the rules".formatted(FITNESS_PACKAGE))
                .isNotEmpty();

        List<String> problems = new ArrayList<>();
        PACKAGE_PREFIX_BY_PROJECT.forEach((project, prefix) -> {
            if (!containsClassesUnder(imported, prefix)) {
                problems.add(("%s — ArchUnit imported no class from %s. Rules naming that prefix "
                        + "evaluate nothing. Add testImplementation(project(\"%s\")) to "
                        + "voyager-fitness/build.gradle.kts, or correct the prefix in "
                        + "PACKAGE_PREFIX_BY_PROJECT.").formatted(project, prefix, project));
            }
            if (ruleDescriptions.stream().noneMatch(description -> description.contains(prefix))) {
                problems.add(("%s — no @ArchTest rule names %s. The module is on the classpath but "
                        + "nothing constrains it. Add a rule scoped to \"%s..\" in "
                        + "voyager-fitness/src/test/java/net/elytrarace/fitness/.")
                        .formatted(project, prefix, prefix));
            }
        });

        assertThat(problems).as("modules of the rebuild without fitness coverage").isEmpty();
    }

    private static boolean containsClassesUnder(JavaClasses classes, String prefix) {
        for (JavaClass candidate : classes) {
            String packageName = candidate.getPackageName();
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads the description of every {@code @ArchTest} rule declared in this module. Scanning rather
     * than listing the rule classes keeps a newly added rule class from being invisible here.
     */
    private static List<String> declaredArchRuleDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (JavaClass fitnessClass : new ClassFileImporter().importPackages(FITNESS_PACKAGE)) {
            for (Field field : load(fitnessClass.getName()).getDeclaredFields()) {
                if (!field.isAnnotationPresent(ArchTest.class) || !ArchRule.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                descriptions.add(read(field).getDescription());
            }
        }
        return descriptions;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, FitnessCoverageTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("%s was imported but cannot be loaded".formatted(name), exception);
        }
    }

    private static ArchRule read(Field field) {
        try {
            field.setAccessible(true);
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("%s is not readable".formatted(field), exception);
        }
    }
}
