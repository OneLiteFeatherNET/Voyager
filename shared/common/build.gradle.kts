plugins {
    java
}
dependencies {
    compileOnly(libs.minecraft.paper)
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
}

