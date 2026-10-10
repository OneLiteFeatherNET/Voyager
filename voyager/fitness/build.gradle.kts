plugins {
    id("voyager.java-conventions")
}

// This module exists to give ArchUnit a classpath containing every module of the rebuild.
// The audit of the tree being replaced found architecture rules that silently checked nothing,
// because they ran on a classpath that did not contain the modules they named.
dependencies {
    testImplementation(project(":voyager:api"))
    testImplementation(project(":voyager:physics"))
    testImplementation(project(":voyager:race"))
    testImplementation(project(":voyager:platform"))
    testImplementation(project(":voyager:server"))
    testImplementation(project(":voyager:setup"))
    testImplementation(libs.archunit.junit5)
}

tasks.test {
    // The frozen baseline lives under src/test/resources/archunit_store and archunit.properties names it relative to the
    // working directory. Pinning that directory to the module makes the path a visible setting, not a Gradle default.
    workingDir = projectDir

    // On CI the baseline may neither be created nor updated (design D3). A frozen rule with no committed entry, or a
    // fixed violation still in the committed store, then fails the build instead of being written silently.
    if (System.getenv("CI") == "true") {
        systemProperty("archunit.freeze.store.default.allowStoreCreation", "false")
        systemProperty("archunit.freeze.store.default.allowStoreUpdate", "false")
    }

    // Only the rebuild's modules. The tree being replaced does not satisfy this rule and is
    // deliberately not held to it; it is deleted at E7.
    systemProperty(
        "voyager.sourceRoots",
        rootProject.subprojects
            .filter { it.path.startsWith(":voyager:") }
            .map { it.projectDir.resolve("src/main/java") }
            .filter { it.isDirectory }
            .joinToString(File.pathSeparator) { it.absolutePath }
    )

    // Every voyager-* module that carries production code. FitnessCoverageTest maps each of these
    // to a package prefix and then proves that ArchUnit both saw the module and has a rule naming
    // it. voyager-fitness has no src/main/java and so is absent, which is what lets it off having
    // rules about itself.
    //
    // Project paths with "/" in place of ":": File.pathSeparator is ":" on Unix, so a path such as
    // ":voyager:api" would split into empty entries and names. "/" keeps each entry whole, and the
    // test turns the separators back into colons.
    systemProperty(
        "voyager.modulesWithSources",
        rootProject.subprojects
            .filter { it.path.startsWith(":voyager:") && it.projectDir.resolve("src/main/java").isDirectory }
            .joinToString(File.pathSeparator) { it.path.replace(':', '/') }
    )
}

// The artifact keeps the name it had before the module moved under voyager/: the project name is now the
// last path segment alone, so the base name is pinned rather than left to derive "fitness".
base {
    archivesName = "voyager-fitness"
}
