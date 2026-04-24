plugins {
    id("java")
    alias(libs.plugins.shadow)
}

// Isolated configuration for HotswapAgent — resolved at task execution time via doFirst,
// so it never pollutes the compile or runtime classpaths.
val hotswapAgent: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(platform(libs.aonyx.bom))
    implementation(libs.minecraft.minestom)
    implementation(libs.aves)
    implementation(libs.xerus)
    implementation(project(":shared:phase"))
    implementation(project(":shared:common"))
    implementation(project(":shared:database"))
    implementation(libs.geometry)
    runtimeOnly(libs.bundles.hibernate)
    runtimeOnly(libs.mariadb)
    runtimeOnly(libs.log4j2.core)
    runtimeOnly(libs.log4j2.slf4j2)

    testImplementation(libs.minecraft.minestom.testing)
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(libs.archunit.junit5)
    testImplementation(platform("org.mockito:mockito-bom:5.20.0"))
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")

    hotswapAgent("org.hotswapagent:hotswap-agent:2.0.3")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.release.set(25)
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    shadowJar {
        archiveClassifier.set("")
        manifest {
            attributes["Main-Class"] = "net.elytrarace.server.VoyagerServer"
        }
    }

    // Full structural hot-reload: JBR 25 (downloaded ~200 MB on first run via Foojay) + HotswapAgent 2.0.3.
    // Supports reloading: new methods, new fields, new classes — anything except changing class hierarchy.
    // Workflow: start this task, connect IDE debugger if needed, then recompile (Ctrl+Shift+F9 in IDEA).
    // Limitation: existing object instances won't reinitialize new fields — they stay null/zero until
    // the relevant objects are reconstructed (e.g. game round restart).
    // Optional JDWP: -PdebugPort=5005 enables a debugger port alongside hot-swap.
    //   ./gradlew :server:runServerHotswap
    //   ./gradlew :server:runServerHotswap -PdebugPort=5005 -Pport=25566
    register<JavaExec>("runServerHotswap") {
        group = "voyager"
        description = "Run Voyager under JBR 25 + HotswapAgent for structural hot-reload (new methods/fields/classes)"
        dependsOn(classes)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("net.elytrarace.server.VoyagerServer")
        javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
            vendor.set(JvmVendorSpec.JETBRAINS)
        })
        val debugPort = providers.gradleProperty("debugPort").orNull
        jvmArgs(
            "-XX:+AllowEnhancedClassRedefinition",
            "-XX:HotswapAgent=external",
            "-XX:+UseZGC",
            "-XX:+UseCompactObjectHeaders",
            "-Xms256M",
            "-Xmx512M",
            "-Dvoyager.dev=true"
        )
        if (debugPort != null) {
            jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$debugPort")
        }
        doFirst {
            jvmArgs("-javaagent:${hotswapAgent.singleFile.absolutePath}")
        }
        workingDir = rootProject.file("run")
        providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
        providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
        val host = providers.gradleProperty("host").orElse("0.0.0.0")
        val port = providers.gradleProperty("port").orElse("25565")
        args(host.get(), port.get())
        standardInput = System.`in`
    }

    // JDWP-only hot-reload: method-body changes without JBR (plain OpenJDK).
    // Suspend the JVM until a debugger connects: -Psuspend=y
    //   ./gradlew :server:runServerDebug
    //   ./gradlew :server:runServerDebug -PdebugPort=5006 -Pport=25566
    register<JavaExec>("runServerDebug") {
        group = "voyager"
        description = "Run Voyager server with JDWP hot-swap (connect IDE debugger to port 5005)"
        dependsOn(classes)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("net.elytrarace.server.VoyagerServer")
        val debugPort = providers.gradleProperty("debugPort").orElse("5005").get()
        val suspend = providers.gradleProperty("suspend").orElse("n").get()
        jvmArgs(
            "-XX:+UseZGC",
            "-XX:+UseCompactObjectHeaders",
            "-Xms256M",
            "-Xmx512M",
            "-Dvoyager.dev=true",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$suspend,address=*:$debugPort"
        )
        workingDir = rootProject.file("run")
        providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
        providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
        val host = providers.gradleProperty("host").orElse("0.0.0.0")
        val port = providers.gradleProperty("port").orElse("25565")
        args(host.get(), port.get())
        standardInput = System.`in`
    }

    // Fast local dev: skips shadow JAR — only recompiles changed classes.
    // Use this for iterative development.
    //   ./gradlew :server:runServerDev
    //   ./gradlew :server:runServerDev -Pport=25566
    register<JavaExec>("runServerDev") {
        group = "voyager"
        description = "Run the Voyager server for local development (no shadow JAR rebuild)"
        dependsOn(classes)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("net.elytrarace.server.VoyagerServer")
        jvmArgs(
            "-XX:+UseZGC",
            "-XX:+UseCompactObjectHeaders",
            "-Xms256M",
            "-Xmx512M",
            "-Dvoyager.dev=true"   // enables /dev-start command to skip lobby countdown
        )
        workingDir = rootProject.file("run")
        providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
        providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
        val host = providers.gradleProperty("host").orElse("0.0.0.0")
        val port = providers.gradleProperty("port").orElse("25565")
        args(host.get(), port.get())
        standardInput = System.`in`
    }

    // Production-like run: builds the fat JAR first.
    // Use this to verify packaging before deployment.
    //   ./gradlew :server:runServer
    register<JavaExec>("runServer") {
        group = "voyager"
        description = "Build shadow JAR and run the Voyager server (production-like)"
        dependsOn(shadowJar)
        classpath = files(shadowJar.get().archiveFile)
        jvmArgs(
            "-XX:+UseZGC",
            "-XX:+UseCompactObjectHeaders",
            "-Xms256M",
            "-Xmx512M"
        )
        workingDir = rootProject.file("run")
        providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
        providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
        val host = providers.gradleProperty("host").orElse("0.0.0.0")
        val port = providers.gradleProperty("port").orElse("25565")
        args(host.get(), port.get())
        standardInput = System.`in`
    }
}
