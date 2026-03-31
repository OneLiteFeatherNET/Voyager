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

    register<JavaExec>("runServer") {
        group = "voyager"
        description = "Build and run the Voyager game server locally"
        dependsOn(shadowJar)
        classpath = files(shadowJar.get().archiveFile)
        jvmArgs(
            "-XX:+UseZGC",
            "-XX:+UseCompactObjectHeaders",
            "-Xms256M",
            "-Xmx512M"
        )
        // Working directory: run/ at the project root
        // Place your data and worlds here:
        //   run/data/cups/cups.json
        //   run/data/maps/{worldName}/map.json
        //   run/worlds/{worldName}/
        workingDir = rootProject.file("run")
        // Override data/worlds paths via Gradle properties if needed:
        //   ./gradlew :server:runServer -PdataPath=/path/to/data -PworldsPath=/path/to/worlds
        providers.gradleProperty("dataPath").orNull?.let { systemProperty("VOYAGER_DATA_PATH", it) }
        providers.gradleProperty("worldsPath").orNull?.let { systemProperty("VOYAGER_WORLDS_PATH", it) }
        // Pass through host/port:
        //   ./gradlew :server:runServer -Phost=0.0.0.0 -Pport=25565
        val host = providers.gradleProperty("host").orElse("0.0.0.0")
        val port = providers.gradleProperty("port").orElse("25565")
        args(host.get(), port.get())
        standardInput = System.`in`
    }
}
