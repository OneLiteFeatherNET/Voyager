plugins {
    java
}
dependencies {
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
