plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":voyager:api"))
    implementation(project(":voyager:physics"))
    implementation(project(":voyager:race"))
    implementation(project(":voyager:platform"))

    // avaje-inject generates the composition root's wiring at compile time (no reflection). The
    // processor runs in this module only; the runtime finds the generated modules through
    // ServiceLoader. jakarta.inject is the JSR-330 annotation set the wiring reads. Nothing outside
    // voyager-server may depend on any of these, see ApiPurityTest.
    implementation(libs.avaje.inject.runtime)
    annotationProcessor(libs.avaje.inject.processor)
    implementation(libs.jakarta.inject)
    testImplementation(libs.avaje.inject.test)

    // Same reasoning as voyager-platform's line: Minestom and falco-anvil both put slf4j-api on the
    // runtime classpath and neither exposes it for compilation, so a module that logs needs it
    // compileOnly. A second pin is not a second version — it is the one Minestom already resolves.
    compileOnly("org.slf4j:slf4j-api:2.0.18")

    // The composition root is the one module that has to answer "what happens when something logs".
    // Log4j2 as the slf4j 2.x provider, exactly as legacy/server/build.gradle.kts does for the tree being
    // replaced, with the configuration in src/main/resources/log4j2.xml. runtimeOnly: no code here
    // names a Log4j type, and it must not.
    runtimeOnly(libs.log4j2.core)
    runtimeOnly(libs.log4j2.slf4j2)

    // LuckPerms, the optional permission backend (ADR-0024). The loader is started by LuckPermsBootstrap when it is on the
    // class path, so it is runtime-only here: no code in this module names it. Guava and failureaccess come with it, because
    // the loader needs them on the runtime class path and Minestom does not bring them (spike 1.1). The loader is excluded
    // from the test class path below, so the boot tests see LuckPerms absent, as they must (see LuckPermsBootstrap).
    runtimeOnly(libs.luckperms.minestom.loader) {
        exclude(group = "net.kyori.adventure")
    }
    runtimeOnly(libs.guava)

    // Pinned to the exact rebuild version for the same reason voyager-platform pins it: the test
    // environment and the Minestom this module compiles against must never be two different versions.
    testImplementation("net.minestom:testing:2026.09.12-26.2")

    // The boot tests read the warning the composition root logs, through a log4j2 appender attached to
    // that logger for one test (LogCapture). runtimeOnly above is not on the test compile classpath, so
    // the appender API has to be declared here. Test scope only: no production code names a Log4j type.
    testImplementation(libs.log4j2.core)
}

// The LuckPerms loader stays off the test class path, as in Cygnus: the boot tests must see LuckPerms absent, and the
// fallback policy is what they exercise. Excluded from the resolved configuration only, so the runtime class path keeps it.
configurations.named("testRuntimeClasspath") {
    exclude(group = "net.luckperms", module = "minestom-loader")
}

tasks.test {
    // CupSessionTest connects fake players through Minestom's test environment. Without this,
    // ConnectionManager.createPlayer's `assert ServerFlag.INSIDE_TEST || ...isVirtual()` fails — the
    // same flag voyager-platform and server set for their own Minestom-backed tests.
    systemProperty("minestom.inside-test", "true")

    // The golden master transcripts (src/test/resources/golden/cup-session) were recorded on a de_DE JVM: decimal
    // commas in "0,000 s", formatted by CupAnnouncer.seconds() with the default locale. Pin the test JVM to that
    // locale so the comparison does not depend on the runner's locale (CI runs en/C.UTF-8). Do not regenerate the
    // goldens to suit a different locale; a change to what the transcripts record is a behaviour change.
    systemProperty("user.language", "de")
    systemProperty("user.country", "DE")

    // Print the full failure of every failed test in the build log, message included. Gradle's default output shows
    // only the exception class and line, so a golden mismatch on a CI runner is otherwise unreadable there.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The working directory every run task uses, and the two directories the server resolves inside it.
//
// THE DOUBLED `run/run` IS NOT A TYPO. workingDir is <root>/run and the server's default data path
// is the *relative* path `run/data`, so it resolves to <root>/run/run/data. That is the convention
// the tree being replaced established (legacy/server/build.gradle.kts + VoyagerServer's system-property
// defaults) and it is where the one real world already sits on a developer checkout. Deriving both
// the Gradle side and the Copy destination from these three values is what keeps the build and the
// runtime from drifting onto two different answers.
val runWorkingDir: File = rootProject.file("run")
val runDataDir: File = runWorkingDir.resolve("run/data")
val runWorldsDir: File = runWorkingDir.resolve("run/worlds")

// The shipped catalogue lives in src/main/resources so that CommittedMapDataTest can read it and so
// that it travels inside the shadow jar. The catalogues themselves take a Path to a directory and
// cannot read a directory that only exists as jar entries (Task 7c, concern 2), so a run has to see
// it unpacked. This is that unpacking, done by the build rather than by the boot path: the server
// never writes to its own data directory.
// Each catalogue directory is its own Sync, so a map or cup deleted from the repository is removed from
// the run directory on the next run. Sync removes everything in its destination that its source does not
// produce, so the destinations are scoped to run/data/maps and run/data/cups: nothing else in run/run
// (worlds, other data) is touched.
val syncRunMaps by tasks.registering(Sync::class) {
    group = "voyager"
    description = "Mirrors the shipped maps into run/run/data/maps; map files not in the repository are removed."
    from(layout.projectDirectory.dir("src/main/resources/maps"))
    into(runDataDir.resolve("maps"))
    // Gradle does not treat a stray file added to a Sync destination as an output change, so an
    // up-to-date result would keep a deleted map alive. The copy is small; always reconcile.
    outputs.upToDateWhen { false }
}

val syncRunCups by tasks.registering(Sync::class) {
    group = "voyager"
    description = "Mirrors the shipped cups into run/run/data/cups; cup files not in the repository are removed."
    from(layout.projectDirectory.dir("src/main/resources/cups"))
    into(runDataDir.resolve("cups"))
    outputs.upToDateWhen { false }
}

val prepareRunData by tasks.registering {
    group = "voyager"
    description = "Installs the shipped map and cup catalogue into the run directory the server reads (maps/ and cups/ only)."
    dependsOn(syncRunMaps, syncRunCups)
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Defensive: keeps every META-INF/services file, including avaje's InjectExtension, merged rather
    // than overwritten by whichever dependency is copied last.
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "net.elytrarace.voyager.server.VoyagerServer"
    }
}

// The JVM flags are the ones the tree being replaced's run tasks use, with one deliberate change:
// the heap. Those tasks cap at 512M, which predates any measurement; ElytraraceBlueAndRed is a
// finite 9429-chunk world and a whole cup flies across all of it, so the cap is raised and the
// reason is written down rather than left as a number nobody chose.
fun JavaExec.voyagerJvmDefaults() {
    group = "voyager"
    jvmArgs(
        "-XX:+UseZGC",
        "-XX:+UseCompactObjectHeaders",
        "-Xms512M",
        "-Xmx2G"
    )
    workingDir = runWorkingDir
    dependsOn(prepareRunData)
    doFirst {
        runWorkingDir.mkdirs()
    }
    // -PdataPath / -PworldsPath override the defaults without editing this file; the property names
    // are the ones VoyagerServer reads, so what Gradle sets and what the server looks for are the
    // same two strings. A missing worlds directory is no longer a warning printed here: the
    // configuration check reports it, with the absolute path, before the server starts.
    // Minestom gates its whole translation path on this and defaults it to OFF, so without it every
    // message reaches the client as a raw key like `voyager.map.banner`. ServerFlag reads it once as
    // a `static final`, so it has to be a JVM argument and not a System.setProperty in main.
    systemProperty("minestom.automatic-component-translation", "true")

    providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
    providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
    providers.gradleProperty("cup").orNull?.let { systemProperty("VOYAGER_CUP", it) }
    providers.gradleProperty("minPlayers").orNull?.let { systemProperty("VOYAGER_MIN_PLAYERS", it) }
    standardInput = System.`in`
}

// The check that every run task waits for: the catalogue, the settings and every world the maps name,
// reported in one pass. It exits 0 with no error and 1 with at least one, starts no game server and
// binds no socket. Run it on its own when a configuration needs checking without starting anything:
//   ./gradlew :voyager:server:validateCatalog
//   ./gradlew :voyager:server:validateCatalog -PworldsPath=/absolute/path/to/worlds
// The deep world check reads every chunk of every referenced world, so it takes as long as the
// worlds are large. Its measured cost is recorded in openspec/changes/add-catalog-validate-task/design.md.
val validateCatalog = tasks.register<JavaExec>("validateCatalog") {
    description = "Checks the settings, the maps and cups and every world they name; exits 0 or 1. " +
            "Starts no server and binds no socket."
    voyagerJvmDefaults()
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.elytrarace.voyager.server.VoyagerServer")
    systemProperty("voyager.config.check", "true")
}

// The skip switch, for the run tasks only. validateCatalog itself is never skipped: asked for by name,
// it always runs. A skipped run says so once, before the server starts, so the skip is never silent.
fun JavaExec.gateOnCatalogCheck() {
    if (project.hasProperty("skipCatalogCheck")) {
        doFirst {
            logger.warn("catalogue check skipped by -PskipCatalogCheck")
        }
    } else {
        dependsOn(validateCatalog)
    }
}

// Fast local dev: classpath, no jar rebuild.
//   ./gradlew :voyager:server:runServerDev
//   ./gradlew :voyager:server:runServerDev -Pport=25566
tasks.register<JavaExec>("runServerDev") {
    description = "Runs the rebuilt Voyager server from the classpath (no shadow jar rebuild)."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.elytrarace.voyager.server.VoyagerServer")
    voyagerJvmDefaults()
    gateOnCatalogCheck()
    val host = providers.gradleProperty("host").orElse("0.0.0.0")
    val port = providers.gradleProperty("port").orElse("25565")
    args(host.get(), port.get())
    // Short lobby, short results screen, and the /race start and /race skip subcommands. A 120 s
    // lobby between two attempts is how a debugging session turns into an afternoon.
    systemProperty("voyager.dev", "true")
}

// Production-like: through the shadow jar, so packaging is exercised too.
//   ./gradlew :voyager:server:runServer
tasks.register<JavaExec>("runServer") {
    description = "Builds the shadow jar and runs the rebuilt Voyager server from it."
    dependsOn(tasks.shadowJar)
    classpath = files(tasks.shadowJar.get().archiveFile)
    mainClass.set("net.elytrarace.voyager.server.VoyagerServer")
    voyagerJvmDefaults()
    gateOnCatalogCheck()
    val host = providers.gradleProperty("host").orElse("0.0.0.0")
    val port = providers.gradleProperty("port").orElse("25565")
    args(host.get(), port.get())
}

// The artifact keeps the name it had before the module moved under voyager/: the project name is now the
// last path segment alone, so the base name is pinned rather than left to derive "server".
base {
    archivesName = "voyager-server"
}
