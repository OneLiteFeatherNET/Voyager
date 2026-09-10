plugins {
    id("voyager.java-conventions")
}

// This module exists to give ArchUnit a classpath containing every module of the rebuild.
// The audit of the tree being replaced found architecture rules that silently checked nothing,
// because they ran on a classpath that did not contain the modules they named.
dependencies {
    testImplementation(project(":voyager-api"))
    testImplementation(libs.archunit.junit5)
}

tasks.test {
    // Only the rebuild's modules. The tree being replaced does not satisfy this rule and is
    // deliberately not held to it; it is deleted at E7.
    systemProperty(
        "voyager.sourceRoots",
        rootProject.subprojects
            .filter { it.name.startsWith("voyager-") }
            .map { it.projectDir.resolve("src/main/java") }
            .filter { it.isDirectory }
            .joinToString(File.pathSeparator) { it.absolutePath }
    )

    systemProperty(
        "voyager.allModules",
        rootProject.subprojects
            .filter { it.name.startsWith("voyager-") && it.name != project.name }
            .joinToString(",") { it.name }
    )

    systemProperty(
        "voyager.fitnessDependencies",
        configurations.testImplementation.get().dependencies
            .filterIsInstance<ProjectDependency>()
            .joinToString(",") { it.name }
    )
}
