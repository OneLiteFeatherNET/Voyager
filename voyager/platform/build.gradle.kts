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
val minestomRebuildVersion = "2026.09.12-26.2"

// Falco pins Minestom through mycelium-bom 1.8.5, which points at the same 2026.08.28-26.2 this
// module pins directly above — so the loader and the server it loads into cannot drift onto two
// Minestom versions without the build saying so. (Before 3.0.0 they could: Falco was built against
// 26.1.2 and declared Minestom compileOnly, which made the binding ours to verify by hand.)
// We still take falco-anvil alone. falco-instance generates worlds and we only read them;
// falco-light is a lighting engine we have no use for until an acceptance run shows a world
// rendering dark.
val falcoVersion = "3.0.0"

dependencies {
    api(project(":voyager:api"))
    api(project(":voyager:physics"))
    api(project(":voyager:race"))

    api("net.minestom:minestom:$minestomRebuildVersion")

    // MiniMessage, which Minestom does not ship: it brings adventure-api 5.2.0 (and the bom that
    // pins it) but not the MiniMessage serializer. Pinned to the same 5.2.0 Minestom already
    // resolves, inline and with the reason at the point of use, exactly as the Minestom pin above
    // is — a different Adventure version here would put two copies of the same interfaces on one
    // classpath. `api` because voyager-server's tests deserialize the same strings this module
    // registers, and because the palette's TagResolver is part of this module's surface.
    api("net.kyori:adventure-text-minimessage:5.2.0")

    api("net.onelitefeather:falco-anvil:$falcoVersion")

    // Gson, and only here. The catalogue reads the committed map and cup JSON; every module that
    // models a race is handed a catalog rather than a file, so none of them needs a parser — see
    // ApiPurityTest's onlyPlatformDependsOnGson. `implementation`, not `api`: the catalogue's public
    // surface is two constructors taking a Path, the *Adapter classes are @ApiStatus.Internal, and
    // keeping Gson off voyager-server's compile classpath means that rule cannot be broken there by
    // accident. Pinned inline for the same reason Minestom is above; 2.14.0 is the version
    // tools/trace-recorder and Paper 26.2 already agree on.
    implementation("com.google.code.gson:gson:2.14.0")

    // The logging facade both Minestom and falco-anvil already put on the runtime classpath, and
    // which neither exposes for compilation. compileOnly is what it costs: this module's published
    // runtime metadata then says nothing about a library its code calls, and a consumer that got
    // Minestom from somewhere else would find out at the first log line. Acceptable only because
    // Minestom cannot run without slf4j at all, so no consumer of this module can be without it.
    compileOnly("org.slf4j:slf4j-api:2.0.18")

    // LuckPerms, behind the PermissionPolicy port (ADR-0024). Compile-only: the adapter is optional at runtime, and the
    // runtime jar comes from the composition roots. The loader is compile-only here as well, because LuckPermsBootstrap
    // starts it, and its compile-time classes are all that is needed. Excluding net.kyori.adventure keeps Minestom's own
    // Adventure version the only one on the class path. The API bundled inside the loader carries the same signatures as
    // net.luckperms:api:5.5 for every member the adapter calls (spike 1.3).
    compileOnly(libs.luckperms.api) {
        exclude(group = "net.kyori.adventure")
    }
    compileOnly(libs.luckperms.minestom.loader) {
        exclude(group = "net.kyori.adventure")
    }

    // Same reasoning as the main artifact above: pinned to the exact rebuild version, not the
    // catalog's `minestom` alias, so the test environment and the api dependency it tests against
    // are never silently on two different Minestom versions.
    testImplementation("net.minestom:testing:$minestomRebuildVersion")

    // Test-scope JSON Schema check for map and cup files (see the json-schema-validator pin in
    // settings.gradle.kts). Never on runtimeClasspath: the adapters are the only runtime check.
    testImplementation(libs.json.schema.validator)

    // Xerus resolves its version from the aonyx-bom platform, same as the tree being replaced
    // (see legacy/server/build.gradle.kts). The BOM constraint has to be `api`, not `implementation`:
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
    // the same flag legacy/server/build.gradle.kts sets for its own Minestom-backed tests.
    systemProperty("minestom.inside-test", "true")
    exclude("**/VelocityExitCustomTpsTest.class")
    // Integration tests read real worlds through Falco; they run in integrationTest, never here.
    useJUnitPlatform { excludeTags("integration") }
}

// The shipped world is untracked (run/ is git-ignored), so the location is the root project's run directory
// unless -PvoyagerItWorld points elsewhere. The IT skips with a message when the folder is absent.
val integrationWorld: String = providers.gradleProperty("voyagerItWorld")
    .getOrElse(rootProject.file("run/run/worlds/ElytraraceBlueAndRed").absolutePath)

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs the @Tag(\"integration\") tests: real worlds read through Falco. Skips with a message " +
            "when the shipped world is not on the checkout."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    systemProperty("minestom.inside-test", "true")
    systemProperty("voyager.it.world", integrationWorld)
}

tasks.check {
    dependsOn(integrationTest)
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

// The artifact keeps the name it had before the module moved under voyager/: the project name is now the
// last path segment alone, so the base name is pinned rather than left to derive "platform".
base {
    archivesName = "voyager-platform"
}
