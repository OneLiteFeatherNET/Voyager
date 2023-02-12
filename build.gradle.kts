plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
    // Shadow
    id("com.github.johnrengelman.shadow") version "7.1.2"
    // Bukkit
    id("xyz.jpenilla.run-paper") version "2.0.1"
    id("org.sonarqube") version "3.5.0.2730"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
    // Changelog
    id("org.jetbrains.changelog") version "2.0.0"
    jacoco
}

group = "net.elytrarace"
val baseVersion = "0.0.1"

repositories {
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    // Paper
    compileOnly("io.papermc.paper:paper-api:1.19.3-R0.1-SNAPSHOT")
    // Database
    implementation("org.jetbrains.exposed", "exposed-core", "0.40.1")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.40.1")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.40.1")
    // Driver
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.2")
    // Commands
    implementation("cloud.commandframework:cloud-core:1.7.1")
    implementation("cloud.commandframework:cloud-annotations:1.7.1")
    implementation("cloud.commandframework:cloud-minecraft-extras:1.7.1")
    implementation("cloud.commandframework:cloud-paper:1.7.1")
    annotationProcessor("cloud.commandframework:cloud-annotations:1.7.1")
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
        minecraftVersion("1.19.3")
    }
    shadowJar {
        archiveFileName.set("${rootProject.name}.${archiveExtension.getOrElse("jar")}")
        // https://github.com/johnrengelman/shadow/issues/107
        isZip64 = true
        // relocate("org.bstats", "builders.volans.bstats")
    }
    getByName<org.sonarqube.gradle.SonarTask>("sonarqube") {
        dependsOn(rootProject.tasks.test)
    }
}
bukkit {
    main = "net.elytrarace.Voyager"
    apiVersion = "1.19"
    authors = listOf("TheMeinerLP")
    depend = listOf("FastAsyncWorldEdit")
}
changelog {
    path.set("${project.projectDir}/CHANGELOG.md")
    header.set(provider { "[${version.get()}] - ${org.jetbrains.changelog.date()}" })
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}

sonarqube {
    properties {
        property("sonar.projectKey", "onelitefeather_projects_voyager_AYZCnikbR8gy89ya5Hi9")
    }
}

version = if (System.getenv().containsKey("CI")) {
    val releaseOrSnapshot = if (System.getenv("CI_COMMIT_BRANCH").equals("main", true)) {
        ""
    } else if(System.getenv("CI_COMMIT_BRANCH").equals("test", true)) {
        "-PREVIEW"
    } else {
        "-SNAPSHOT"
    }
    "$baseVersion$releaseOrSnapshot+${System.getenv("CI_COMMIT_SHORT_SHA")}"
} else {
    "$baseVersion-SNAPSHOT"
}