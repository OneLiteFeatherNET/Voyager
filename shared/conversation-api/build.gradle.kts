plugins {
    java
}

dependencies {
    compileOnly("net.kyori:adventure-api:4.21.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.21.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    compileOnly("org.jetbrains:annotations:26.1.0")
    compileOnly("org.apache.commons:commons-lang3:3.17.0")
    compileOnly("com.google.guava:guava:33.4.0-jre")
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
}
