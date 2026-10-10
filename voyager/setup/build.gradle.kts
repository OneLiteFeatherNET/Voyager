plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
}

// The setup server: a second composition root, next to voyager-server. It depends on voyager-api and
// voyager-platform and on nothing else of the rebuild (design decision 1, ApiPurityTest's setup rules).
dependencies {
    implementation(project(":voyager:api"))
    implementation(project(":voyager:platform"))

    // avaje-inject generates the wiring for SetupBeans at compile time, as it does for voyager-server.
    // DI annotations stay in net.elytrarace.voyager.setup.inject and in SetupServer, see ApiPurityTest.
    implementation(libs.avaje.inject.runtime)
    annotationProcessor(libs.avaje.inject.processor)
    implementation(libs.jakarta.inject)
    testImplementation(libs.avaje.inject.test)

    // Same reasoning as voyager-server and voyager-platform: Minestom brings slf4j-api at runtime and
    // does not expose it for compilation, so a module that logs declares it compileOnly.
    compileOnly("org.slf4j:slf4j-api:2.0.18")
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

    // Pinned to the rebuild's Minestom version, as voyager-platform pins it: the test environment and
    // the Minestom this module compiles against must never be two different versions.
    testImplementation("net.minestom:testing:2026.09.12-26.2")
    testImplementation(libs.log4j2.core)
}

// The LuckPerms loader stays off the test class path, as in Cygnus: the boot tests must see LuckPerms absent, and the
// fallback policy is what they exercise. Excluded from the resolved configuration only, so the runtime class path keeps it.
configurations.named("testRuntimeClasspath") {
    exclude(group = "net.luckperms", module = "minestom-loader")
}

tasks.test {
    // Minestom's test environment creates players; without this flag ConnectionManager refuses them.
    systemProperty("minestom.inside-test", "true")

    // Print the full failure of every failed test in the build log, message included. Gradle's default output shows
    // only the exception class and line, so a failure on a CI runner is otherwise unreadable there.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The directories the setup server reads, relative to the project root. The server's defaults
// (SetupSettings) are these same relative paths, so the run task's working directory is the project
// root and the defaults resolve to run-setup/data and run-setup/worlds without a doubled prefix.
val runSetupDataDir: File = rootProject.file("run-setup/data")
val runSetupWorldsDir: File = rootProject.file("run-setup/worlds")

// Fast local dev: the classpath, no jar rebuild.
//   ./gradlew :voyager:setup:runSetupDev
//   ./gradlew :voyager:setup:runSetupDev -Pport=25566 -PdataPath=... -PworldsPath=...
tasks.register<JavaExec>("runSetupDev") {
    group = "voyager"
    description = "Runs the Voyager setup server (map authoring) from the classpath."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("net.elytrarace.voyager.setup.SetupServer")
    workingDir = rootProject.projectDir
    jvmArgs("-XX:+UseZGC", "-XX:+UseCompactObjectHeaders", "-Xms256M", "-Xmx1G")
    // Automatic component translation is a JVM flag: ServerFlag reads it once, as voyager-server's run tasks note.
    systemProperty("minestom.automatic-component-translation", "true")
    providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
    providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
    doFirst {
        // The server refuses a missing directory, so a first run creates the two defaults here.
        if (!providers.gradleProperty("dataPath").isPresent) runSetupDataDir.mkdirs()
        if (!providers.gradleProperty("worldsPath").isPresent) runSetupWorldsDir.mkdirs()
    }
    val host = providers.gradleProperty("host").orElse("0.0.0.0")
    val port = providers.gradleProperty("port").orElse("25566")
    args(host.get(), port.get())
    standardInput = System.`in`
}

// Spike sources (src/spike): Minestom 26.2 API checks behind docs/research/006. They are not part of the
// test task, which runs on every build; the spike task runs them on demand.
val spike: SourceSet by sourceSets.creating
configurations.named(spike.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named(spike.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}
spike.compileClasspath += sourceSets.main.get().output
spike.runtimeClasspath += sourceSets.main.get().output

tasks.register<Test>("spike") {
    group = "verification"
    description = "Runs the Minestom 26.2 spikes in src/spike (display transform, wand identity, void world)."
    testClassesDirs = spike.output.classesDirs
    classpath = spike.runtimeClasspath
    useJUnitPlatform()
    systemProperty("minestom.inside-test", "true")
}

// Production-like fat jar, as voyager-server builds one: a CloudNet service starts it with `java -jar`.
// Run it with the automatic-translation flag as a JVM argument (ServerFlag reads it once, see runSetupDev above):
//   java -Dminestom.automatic-component-translation=true -jar voyager-setup-<version>.jar 0.0.0.0 25566
tasks.shadowJar {
    archiveClassifier.set("")
    // Keeps every META-INF/services file, including avaje's generated wiring, merged rather than overwritten.
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "net.elytrarace.voyager.setup.SetupServer"
    }
}

// The artifact keeps the name it had before the module moved under voyager/: the project name is now the
// last path segment alone, so the base name is pinned rather than left to derive "setup".
base {
    archivesName = "voyager-setup"
}
