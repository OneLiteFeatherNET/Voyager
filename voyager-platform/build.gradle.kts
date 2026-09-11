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

    // Same reasoning as the main artifact above: pinned to the exact rebuild version, not the
    // catalog's `minestom` alias, so the test environment and the api dependency it tests against
    // are never silently on two different Minestom versions.
    testImplementation("net.minestom:testing:$minestomRebuildVersion")

    // Xerus resolves its version from the aonyx-bom platform, same as the tree being replaced
    // (see server/build.gradle.kts). The BOM constraint has to be `api`, not `implementation`:
    // voyager-server consumes voyager-platform's unversioned `libs.xerus` transitively, and an
    // implementation-scoped platform constraint does not reach a downstream consumer's classpath —
    // it would leave voyager-server unable to resolve a version for Xerus at all.
    api(platform(libs.aonyx.bom))
    api(libs.xerus)
}

// VelocityExitCustomTpsTest proves VelocityExit reads ServerFlag.SERVER_TICKS_PER_SECOND rather
// than a literal 20. That flag is a `static final int` read once, from a system property, the
// first time ServerFlag loads in a JVM, so System.setProperty from inside a test method would be
// too late — something else in the shared test JVM has likely already touched the class by then.
// The only reliable way to set it is a JVM's own startup arguments, which only a dedicated forked
// test task can guarantee, so that one test class runs here instead of in the default `test` task
// — which stays on Minestom's default 20 TPS, matching every other test's exactness assertions.
tasks.test {
    // VelocityExitTest creates a real Player through Minestom's test environment. Without this,
    // ConnectionManager.createPlayer's `assert ServerFlag.INSIDE_TEST || ...isVirtual()` fails —
    // the same flag server/build.gradle.kts sets for its own Minestom-backed tests.
    systemProperty("minestom.inside-test", "true")
    exclude("**/VelocityExitCustomTpsTest.class")
}

val tpsFlagTest = tasks.register<Test>("tpsFlagTest") {
    description = "Runs VelocityExitCustomTpsTest with minestom.tps set before the JVM starts, " +
            "proving VelocityExit converts against ServerFlag.SERVER_TICKS_PER_SECOND and not a " +
            "literal 20."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/VelocityExitCustomTpsTest.class")
    systemProperty("minestom.tps", "7")
    systemProperty("minestom.inside-test", "true")
}

tasks.check {
    dependsOn(tpsFlagTest)
}
