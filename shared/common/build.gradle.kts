plugins {
    `java-library`
}
dependencies {
    // Math
    implementation(libs.geometry)
    // Adventure API (was provided transitively by Paper)
    api(platform("net.kyori:adventure-bom:4.26.1"))
    api("net.kyori:adventure-api")
    api("net.kyori:adventure-text-minimessage")
    api("net.kyori:adventure-text-serializer-plain")
    // Gson (was provided transitively by Paper)
    api("com.google.code.gson:gson:2.14.0")
    // Guava (was provided transitively by Paper)
    implementation("com.google.guava:guava:33.6.0-jre")
    // Logging (was provided transitively by Paper)
    api("org.slf4j:slf4j-api:2.0.17")
    // Annotations
    compileOnly(libs.jetbrains.annotations)
    // Test
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
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
