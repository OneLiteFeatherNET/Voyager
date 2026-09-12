plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
}

// io.airlift:guice, not upstream com.google.inject:guice: the maintained fork carries no ASM and no
// Unsafe. Package names are unchanged (com.google.inject.*), so ApiPurityTest's DI rule matches on
// that package regardless of which artifact supplied it. Pinned inline for the same reason Minestom
// 26.2 is pinned inline in voyager-platform/build.gradle.kts — there is no existing catalog alias to
// collide with here, but a bare version string keeps the pin visible at the point of use rather than
// buried in a catalog only this one module reads.
val guiceVersion = "10"

dependencies {
    implementation(project(":voyager-api"))
    implementation(project(":voyager-physics"))
    implementation(project(":voyager-race"))
    implementation(project(":voyager-platform"))

    implementation("io.airlift:guice:$guiceVersion")

    // Same reasoning as voyager-platform's line: Minestom and falco-anvil both put slf4j-api on the
    // runtime classpath and neither exposes it for compilation, so a module that logs needs it
    // compileOnly. A second pin is not a second version — it is the one Minestom already resolves.
    compileOnly("org.slf4j:slf4j-api:2.0.18")

    // The composition root is the one module that has to answer "what happens when something logs".
    // Log4j2 as the slf4j 2.x provider, exactly as server/build.gradle.kts does for the tree being
    // replaced, with the configuration in src/main/resources/log4j2.xml. runtimeOnly: no code here
    // names a Log4j type, and it must not.
    runtimeOnly(libs.log4j2.core)
    runtimeOnly(libs.log4j2.slf4j2)

    // Pinned to the exact rebuild version for the same reason voyager-platform pins it: the test
    // environment and the Minestom this module compiles against must never be two different versions.
    testImplementation("net.minestom:testing:2026.08.28-26.2")
}

tasks.test {
    // CupSessionTest connects fake players through Minestom's test environment. Without this,
    // ConnectionManager.createPlayer's `assert ServerFlag.INSIDE_TEST || ...isVirtual()` fails — the
    // same flag voyager-platform and server set for their own Minestom-backed tests.
    systemProperty("minestom.inside-test", "true")
}

// The working directory every run task uses, and the two directories the server resolves inside it.
//
// THE DOUBLED `run/run` IS NOT A TYPO. workingDir is <root>/run and the server's default data path
// is the *relative* path `run/data`, so it resolves to <root>/run/run/data. That is the convention
// the tree being replaced established (server/build.gradle.kts + VoyagerServer's system-property
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
val prepareRunData by tasks.registering(Copy::class) {
    group = "voyager"
    description = "Installs the shipped map and cup catalogue into the run directory the server reads."
    from(layout.projectDirectory.dir("src/main/resources")) {
        include("maps/**")
        include("cups/**")
    }
    into(runDataDir)
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "net.elytrarace.voyager.server.VoyagerServer"
    }
}

// The JVM flags are the ones the tree being replaced's run tasks use, with one deliberate change:
// the heap. Those tasks cap at 512M, which predates any measurement; ElytraraceBlueAndRed is a
// finite 9429-chunk world and a whole cup flies across all of it, so the cap is raised and the
// reason is written down rather than left as a number nobody chose.
fun JavaExec.voyagerRunDefaults() {
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
        // The worlds are Anvil directories and are not in the repository, so this is the one input
        // a fresh checkout will not have. Said here, before the JVM starts, because the same failure
        // arriving as a stack trace forty lines into a server log is the one the brief for this task
        // calls "no such world on a machine where the world plainly exists".
        if (!runWorldsDir.isDirectory) {
            logger.warn(
                "Voyager: no worlds directory at {} — the server will refuse to start. "
                    + "Put each map's Anvil world directory there, or pass -PworldsPath=<dir>.",
                runWorldsDir.absolutePath
            )
        }
    }
    // -PdataPath / -PworldsPath override the defaults without editing this file; the property names
    // are the ones VoyagerServer reads, so what Gradle sets and what the server looks for are the
    // same two strings.
    // Minestom gates its whole translation path on this and defaults it to OFF, so without it every
    // message reaches the client as a raw key like `voyager.map.banner`. ServerFlag reads it once as
    // a `static final`, so it has to be a JVM argument and not a System.setProperty in main.
    systemProperty("minestom.automatic-component-translation", "true")

    providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
    providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
    providers.gradleProperty("cup").orNull?.let { systemProperty("VOYAGER_CUP", it) }
    val host = providers.gradleProperty("host").orElse("0.0.0.0")
    val port = providers.gradleProperty("port").orElse("25565")
    args(host.get(), port.get())
    standardInput = System.`in`
}

// Fast local dev: classpath, no jar rebuild.
//   ./gradlew :voyager-server:runServerDev
//   ./gradlew :voyager-server:runServerDev -Pport=25566
tasks.register<JavaExec>("runServerDev") {
    description = "Runs the rebuilt Voyager server from the classpath (no shadow jar rebuild)."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.elytrarace.voyager.server.VoyagerServer")
    voyagerRunDefaults()
    // Short lobby, short results screen, and the /race start and /race skip subcommands. A 120 s
    // lobby between two attempts is how a debugging session turns into an afternoon.
    systemProperty("voyager.dev", "true")
}

// Production-like: through the shadow jar, so packaging is exercised too.
//   ./gradlew :voyager-server:runServer
tasks.register<JavaExec>("runServer") {
    description = "Builds the shadow jar and runs the rebuilt Voyager server from it."
    dependsOn(tasks.shadowJar)
    classpath = files(tasks.shadowJar.get().archiveFile)
    mainClass.set("net.elytrarace.voyager.server.VoyagerServer")
    voyagerRunDefaults()
}
