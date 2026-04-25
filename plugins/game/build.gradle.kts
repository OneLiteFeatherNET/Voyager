import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("java")
    id("maven-publish")
    id("jacoco")
    alias(libs.plugins.run.paper)
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml)
}
dependencies {
    compileOnly(libs.minecraft.paper)
    implementation(libs.minecraft.cloud.paper)
    implementation(project(":shared:common"))
    implementation(project(":shared:database"))
    // Math
    implementation(libs.geometry)
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
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
        minecraftVersion("1.21.5")
    }
}

paper {
    main = "net.elytrarace.game.ElytraRace"
    name = "ElytraRace-Game"
    version = rootProject.version.toString()
    apiVersion = "1.21"
    authors = listOf("TheMeinerLP")
    serverDependencies {
        register("Multiverse-Core") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
    }
}

