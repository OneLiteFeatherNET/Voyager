package net.elytrarace.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules enforcing platform-layer isolation:
 * <ul>
 *   <li>{@code shared/common} and {@code shared/phase} must never reference Minestom.</li>
 *   <li>{@code shared/common} must never reference Paper/Bukkit.</li>
 *   <li>{@code server} must never reference Paper/Bukkit.</li>
 * </ul>
 *
 * <p>This guarantees that the shared modules remain platform-agnostic and
 * the server module is Minestom-only.
 */
@AnalyzeClasses(packages = "net.elytrarace", importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule shared_common_must_not_use_minestom =
            noClasses().that().resideInAPackage("net.elytrarace.common..")
                    .should().dependOnClassesThat().resideInAPackage("net.minestom..")
                    .because("shared/common must stay platform-agnostic — no Minestom imports allowed");

    @ArchTest
    static final ArchRule shared_phase_must_not_use_minestom =
            noClasses().that().resideInAPackage("net.elytrarace.api.phase..")
                    .should().dependOnClassesThat().resideInAPackage("net.minestom..")
                    .because("shared/phase must stay platform-agnostic — no Minestom imports allowed");

    @ArchTest
    static final ArchRule server_must_not_use_paper =
            noClasses().that().resideInAPackage("net.elytrarace.server..")
                    .should().dependOnClassesThat().resideInAPackage("org.bukkit..")
                    .because("server module is Minestom-only; Paper API is forbidden");

    @ArchTest
    static final ArchRule shared_common_must_not_use_paper =
            noClasses().that().resideInAPackage("net.elytrarace.common..")
                    .should().dependOnClassesThat().resideInAPackage("org.bukkit..")
                    .because("shared/common must be platform-agnostic — no Paper/Bukkit imports allowed");
}
