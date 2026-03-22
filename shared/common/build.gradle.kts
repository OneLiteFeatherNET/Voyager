plugins {
    `java-library`
}
dependencies {
    // Math
    implementation(libs.geometry)
    // Adventure API (was provided transitively by Paper)
    api(platform("net.kyori:adventure-bom:4.21.0"))
    api("net.kyori:adventure-api")
    api("net.kyori:adventure-text-minimessage")
    api("net.kyori:adventure-text-serializer-plain")
    // Gson (was provided transitively by Paper)
    api("com.google.code.gson:gson:2.12.1")
    // Guava (was provided transitively by Paper)
    implementation("com.google.guava:guava:33.4.0-jre")
    // Logging (was provided transitively by Paper)
    api("org.slf4j:slf4j-api:2.0.16")
    // Annotations
    compileOnly(libs.jetbrains.annotations)
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }
}
