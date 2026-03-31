plugins {
    id("java")
    alias(libs.plugins.shadow)
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
    runtimeOnly(libs.log4j2.core)
    runtimeOnly(libs.log4j2.slf4j2)

    testImplementation(libs.minecraft.minestom.testing)
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
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
