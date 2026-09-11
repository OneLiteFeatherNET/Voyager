plugins {
    id("voyager.java-conventions")
    `java-library`
}

// Minestom 26.2, pinned inline, not through the catalog: the catalog's `minestom` alias in
// settings.gradle.kts is 2026.05.11-1.21.11 and belongs to the tree being replaced (server,
// plugins/*) — it stays untouched. A second catalog alias for the same artifact was tried for
// paper-api and reverted (see tools/trace-recorder/build.gradle.kts): Renovate matches coordinates
// rather than alias names, so two aliases for net.minestom:minestom would give it two incompatible
// pins to reconcile, and a Gradle version conflict resolves to the higher one — silently compiling
// the tree being replaced against 26.2.
val minestomRebuildVersion = "2026.08.28-26.2"

dependencies {
    api(project(":voyager-api"))
    api(project(":voyager-physics"))
    api(project(":voyager-race"))

    api("net.minestom:minestom:$minestomRebuildVersion")

    // Xerus resolves its version from the aonyx-bom platform, same as the tree being replaced
    // (see server/build.gradle.kts). The BOM constraint has to be `api`, not `implementation`:
    // voyager-server consumes voyager-platform's unversioned `libs.xerus` transitively, and an
    // implementation-scoped platform constraint does not reach a downstream consumer's classpath —
    // it would leave voyager-server unable to resolve a version for Xerus at all.
    api(platform(libs.aonyx.bom))
    api(libs.xerus)
}
