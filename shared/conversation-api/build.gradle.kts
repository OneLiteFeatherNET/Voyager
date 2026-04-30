plugins {
    java
}

dependencies {
    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    compileOnly("org.jetbrains:annotations:26.1.0")
    compileOnly("org.apache.commons:commons-lang3:3.17.0")
    compileOnly("com.google.guava:guava:33.6.0-jre")
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
