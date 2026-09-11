import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    compileOnly(libs.findLibrary("jetbrains-annotations").orElseThrow())
    testImplementation(platform(libs.findLibrary("junit-bom").orElseThrow()))
    testImplementation(libs.findLibrary("junit-jupiter").orElseThrow())
    testImplementation(libs.findLibrary("assertj").orElseThrow())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
