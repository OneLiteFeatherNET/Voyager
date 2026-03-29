plugins {
    id("java")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(platform(libs.aonyx.bom))
    implementation(libs.minecraft.minestom)
    implementation(libs.aves)
    implementation(libs.xerus)
    implementation(project(":shared:common"))
    implementation(project(":shared:database"))
    implementation(libs.geometry)

    testImplementation(libs.minecraft.minestom.testing)
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
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
}
