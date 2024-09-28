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
    implementation(libs.minecraft.cloud.paper)
    implementation(project(":shared:common"))
    implementation(project(":shared:conversation-api"))
    // FAWE
    implementation(platform(libs.fawe.bom))
    compileOnly(libs.bundles.fawe)
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
    main = "net.elytrarace.setup.ElytraRace"
    name = "ElytraRace"
    version = rootProject.version.toString()
    apiVersion = "1.21"
    authors = listOf("TheMeinerLP")
    serverDependencies {
        register("FastAsyncWorldEdit")
        register("VoidGen")
    }
}

