import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder
import org.ajoberstar.grgit.Grgit
import java.util.*

plugins {
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.8.20"
    // Shadow
    id("com.github.johnrengelman.shadow") version "7.1.2"
    // Bukkit
    id("xyz.jpenilla.run-paper") version "2.1.0"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.3"
    id("org.ajoberstar.grgit") version "5.2.0"
    jacoco
}

group = "net.elytrarace"
if (!File("$rootDir/.git").exists()) {
    logger.lifecycle(
            """
    **************************************************************************************
    You need to fork and clone this repository! Don't download a .zip file.
    If you need assistance, consult the GitHub docs: https://docs.github.com/get-started/quickstart/fork-a-repo
    **************************************************************************************
    """.trimIndent()
    ).also { System.exit(1) }
}
var baseVersion by extra("1.0.0")
var extension by extra("")
var snapshot by extra("-SNAPSHOT")

ext {
    val git: Grgit = Grgit.open {
        dir = File("$rootDir/.git")
    }
    val revision = git.head().abbreviatedId
    extension = "%s+%s".format(Locale.ROOT, snapshot, revision)
}

version = "%s%s".format(Locale.ROOT, baseVersion, extension)

repositories {
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    // Paper
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")
    // Math
    implementation("org.apache.commons:commons-geometry-euclidean:1.0")
    // Config
    api("org.jetbrains.kotlinx:kotlinx-serialization-hocon:1.4.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0-RC")
    // Caching
    implementation("com.google.guava:guava:31.1-jre")
    // Database
    implementation("org.jetbrains.exposed", "exposed-core", "0.40.1")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.40.1")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.40.1")
    api("com.zaxxer:HikariCP:5.0.1")
    // Driver
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.2")
    bukkitLibrary("com.h2database:h2:2.1.214")
    // Commands
    implementation("cloud.commandframework:cloud-annotations:1.8.0")
    implementation("cloud.commandframework:cloud-minecraft-extras:1.8.0")
    implementation("cloud.commandframework:cloud-paper:1.8.0")
    annotationProcessor("cloud.commandframework:cloud-annotations:1.8.0")
    implementation("me.lucko:commodore:2.2")
    // FAWE
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.5.0")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.5.0") {
        isTransitive = false
    }

}
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
    jacocoTestReport {
        dependsOn(rootProject.tasks.test)
        reports {
            xml.required.set(true)
        }
    }
    test {
        finalizedBy(rootProject.tasks.jacocoTestReport)
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        maxHeapSize = "512m"
        maxParallelForks = if (System.getenv().containsKey("CI")) {
            8
        } else {
            Runtime.getRuntime().availableProcessors() / 2
        }
    }
    runServer {
        minecraftVersion("1.19.4")
    }
    shadowJar {
        archiveFileName.set("${rootProject.name}.${archiveExtension.getOrElse("jar")}")
        // https://github.com/johnrengelman/shadow/issues/107
        isZip64 = true
        // relocate("org.bstats", "builders.volans.bstats")
    }
}
bukkit {
    load = PluginLoadOrder.POSTWORLD
    main = "net.elytrarace.Voyager"
    apiVersion = "1.19"
    authors = listOf("TheMeinerLP")
    depend = listOf("FastAsyncWorldEdit", "VoidGen")
}