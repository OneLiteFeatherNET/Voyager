plugins {
    `java-library`
}
dependencies {
    api(project(":shared:common"))
    implementation(libs.geometry)
    compileOnly(libs.jetbrains.annotations)
    // Test
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
