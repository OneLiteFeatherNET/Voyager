plugins {
    id("java")
}

version = "1.12.0" // x-release-please-version

group = "net.onelitefeather"

subprojects {
    repositories {
        mavenCentral()
        // Where the Falco artefacts themselves live, publicly. Resolving them still needs the
        // authenticated OneLiteFeatherRepository below as well: falco-anvil's POM imports
        // net.onelitefeather:mycelium-bom:1.7.2, and that version is in neither this repository
        // (which has only 1.8.3 to 1.8.5) nor Maven Central. Both entries stay until Falco publishes
        // against mycelium-bom 1.8.5.
        maven("https://repo.onelitefeather.dev/releases")
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
