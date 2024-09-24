plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("net.minecrell.plugin-yml.paper") version "0.6.0"
    id("com.gradleup.shadow") version "8.3.2"
    `maven-publish`
    jacoco
}
dependencies {
    compileOnly(libs.minecraft.paper)
    implementation(project(":shared:phase"))
    implementation(project(":shared:common"))
    implementation(project(":shared:database"))
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }
    jacocoTestReport {
        dependsOn(project.tasks.test)
        reports {
            xml.required.set(true)
        }
    }
    test {
        finalizedBy(project.tasks.jacocoTestReport)
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    runServer {
        minecraftVersion("1.21.1")
    }
}

paper {
    main = "net.elytrarace.game.ElytraRace"
    name = "ElytraRace"
    version = rootProject.version.toString()
    apiVersion = "1.21"
    authors = listOf("TheMeinerLP")
}

