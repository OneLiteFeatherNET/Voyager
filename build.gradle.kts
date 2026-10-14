import java.util.zip.ZipFile

plugins {
    id("java")
}

version = "1.16.0" // x-release-please-version

group = "net.onelitefeather"

subprojects {
    repositories {
        mavenCentral()
        // Where the Falco artefacts live, publicly and with no credentials — including the
        // mycelium-bom 1.8.9 their POMs import, which is what makes that true. It was not true
        // before Falco 3.0.0: 2.x imported mycelium-bom 1.7.2, which is in neither this repository
        // nor Maven Central, so resolving it needed the authenticated OneLiteFeatherRepository
        // below. Said here so nobody re-derives that conclusion from a 2.x artefact.
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

// No shipped fat jar may carry a CloudNet class. The bridge loads from its own extension class loader beside the node's
// CloudNet; a copy inside a root's jar would be a second, unmanaged CloudNet (ADR-0026). Checked by entry name, so the
// check does not depend on how a jar was assembled. Runs under check, so it is part of ./gradlew build.
val fatJarProjects = listOf(":voyager:server", ":voyager:setup")
val verifyNoCloudNetInFatJars by tasks.registering {
    group = "verification"
    description = "Fails when a fat jar of voyager-server or voyager-setup holds an eu/cloudnetservice entry."
    fatJarProjects.forEach { dependsOn("$it:shadowJar") }
    doLast {
        val offenders = fatJarProjects.flatMap { path ->
            val jar = project(path).tasks.getByName("shadowJar").outputs.files.singleFile
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("eu/cloudnetservice/") }
                    .map { "${jar.name}: $it" }
                    .toList()
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException("CloudNet classes found in fat jars, which must never contain them:\n" + offenders.joinToString("\n"))
        }
    }
}

// The bridge is loaded beside the node's CloudNet, in its own class loader. A copy of voyager-api inside the bridge jar would be a
// second hub-handoff slot, so PlayerHandoffs would split in two and the server would never see the bridge's install (ADR-0033).
// The bridge's own package is the only Voyager package its jar may hold.
val bridgeProject = ":voyager:cloudnet-bridge"
val bridgeOwnPackage = "net/elytrarace/voyager/cloudnet/bridge/"
val verifyNoVoyagerApiInBridgeJar by tasks.registering {
    group = "verification"
    description = "Fails when the voyager-cloudnet-bridge jar holds a Voyager class outside its own package."
    dependsOn("$bridgeProject:jar")
    doLast {
        val jar = project(bridgeProject).tasks.getByName("jar").outputs.files.singleFile
        val offenders = ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { it.startsWith("net/elytrarace/voyager/") && !it.startsWith("net/elytrarace/voyager/cloudnet/bridge/") }
                .map { "${jar.name}: $it" }
                .toList()
        }
        if (offenders.isNotEmpty()) {
            throw GradleException("Voyager classes found in the bridge jar, which may carry only $bridgeOwnPackage:\n" +
                offenders.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(verifyNoCloudNetInFatJars)
    dependsOn(verifyNoVoyagerApiInBridgeJar)
}
