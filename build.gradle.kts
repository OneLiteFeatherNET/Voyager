plugins {
    id("java")
}

version = "1.8.8" // x-release-please-version

group = "net.onelitefeather"
// version is managed by semantic-release via gradle.properties

subprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
        maven {
            name = "OneLiteFeatherRepository"
            url = uri("https://repo.onelitefeather.dev/onelitefeather")
            if (System.getenv("CI") != null) {
                credentials {
                    username = System.getenv("ONELITEFEATHER_MAVEN_USERNAME")
                    password = System.getenv("ONELITEFEATHER_MAVEN_PASSWORD")
                }
            } else {
                credentials(PasswordCredentials::class)
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }
}

// gradle.properties used to carry the version, which Gradle applies to every
// project in the build. The version now lives above, so pass it down explicitly.
allprojects {
    version = rootProject.version
}
