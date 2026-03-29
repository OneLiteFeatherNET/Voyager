plugins {
    id("java")
}

group = "net.onelitefeather"
version = "0.0.1-SNAPSHOT"

subprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
    }
}
