rootProject.name = "Voyager"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("paper", "1.21.5-R0.1-SNAPSHOT")
            version("minestom", "2026.05.11-1.21.11")
            version("hibernate", "7.3.10.Final")
            version("flyway", "12.10.0")
            version("mariadb-client", "3.5.10")
            version("jetbrains-annotations", "26.1.0")
            version("fawe-bom", "1.56")
            version("commons-geometry-euclidean", "1.0")
            version("archunit", "1.5.0")
            version("run-paper", "3.0.2")
            version("shadow", "9.4.3")
            version("plugin-yml", "0.6.0")

            library("minecraft.paper","io.papermc.paper", "paper-api").versionRef("paper")
            library("minecraft.minestom", "net.minestom", "minestom").versionRef("minestom")
            library("minecraft.minestom.testing", "net.minestom", "testing").versionRef("minestom")
            library("minecraft.cloud.paper", "org.incendo", "cloud-paper").version("2.0.0")
            library("minecraft.cloud.minestom", "org.incendo", "cloud-minestom").version("2.0.0-SNAPSHOT")

            // OneLiteFeather Libraries (via aonyx-bom)
            version("aonyx-bom", "0.7.1")
            library("aonyx.bom", "net.onelitefeather", "aonyx-bom").versionRef("aonyx-bom")
            library("aves", "net.theevilreaper", "aves").withoutVersion()
            library("xerus", "net.theevilreaper", "xerus").withoutVersion()
            library("hibernate.core", "org.hibernate.orm", "hibernate-core").versionRef("hibernate")
            library("hibernate.hikaricp", "org.hibernate.orm", "hibernate-hikaricp").versionRef("hibernate")
            library("flyway.core", "org.flywaydb", "flyway-core").versionRef("flyway")
            library("flyway.mysql", "org.flywaydb", "flyway-mysql").versionRef("flyway")
            library("mariadb", "org.mariadb.jdbc", "mariadb-java-client").versionRef("mariadb-client")
            library("jetbrains.annotations", "org.jetbrains", "annotations").versionRef("jetbrains-annotations")
            library("fawe.bom", "com.intellectualsites.bom","bom-newest").versionRef("fawe-bom")
            library("fawe.core", "com.fastasyncworldedit", "FastAsyncWorldEdit-Core").withoutVersion()
            library("fawe.bukkit", "com.fastasyncworldedit", "FastAsyncWorldEdit-Bukkit").withoutVersion()
            library("geometry", "org.apache.commons", "commons-geometry-euclidean").versionRef("commons-geometry-euclidean")
            library("archunit.junit5", "com.tngtech.archunit", "archunit-junit5").versionRef("archunit")

            // JSON Schema 2020-12 validator, test scope only (voyager-platform): it checks the committed map and
            // cup files and the test fixtures against schema/*.schema.json. It is never on a main classpath, so
            // the loader stays the only runtime check. Pinned to 3.0.7, released 2026-08-20. 3.0.8 was released
            // 2026-09-30, inside the two-week cooling window before 2026-10-09, so it is not used yet.
            // Approved by the owner on 2026-10-09 (openspec change simplify-map-data-format, design decision 5).
            version("json-schema-validator", "3.0.7")
            library("json.schema.validator", "com.networknt", "json-schema-validator").versionRef("json-schema-validator")

            version("junit", "6.1.1")
            version("assertj", "3.27.7")
            library("junit.bom", "org.junit", "junit-bom").versionRef("junit")
            library("junit.jupiter", "org.junit.jupiter", "junit-jupiter").withoutVersion()
            library("assertj", "org.assertj", "assertj-core").versionRef("assertj")

            // Logging — Log4j2 as SLF4J 2.x provider (Minestom ships SLF4J 2.x API)
            version("log4j2", "2.25.5")
            library("log4j2.core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j2")
            library("log4j2.slf4j2", "org.apache.logging.log4j", "log4j-slf4j2-impl").versionRef("log4j2")

            // Compile-time DI for the composition root only (voyager-server). The processor generates the
            // wiring; no other module may depend on any of these or on jakarta.inject.
            version("avaje-inject", "12.7")
            library("avaje.inject.runtime", "io.avaje", "avaje-inject").versionRef("avaje-inject")
            library("avaje.inject.processor", "io.avaje", "avaje-inject-generator").versionRef("avaje-inject")
            library("avaje.inject.test", "io.avaje", "avaje-inject-test").versionRef("avaje-inject")
            library("jakarta.inject", "jakarta.inject", "jakarta.inject-api").version("2.0.1")

            // LuckPerms, the optional permission backend behind the PermissionPolicy port (ADR-0024). The API is compileOnly
            // in voyager-platform. The Minestom loader is compileOnly there too, because LuckPermsBootstrap starts it, and
            // runtimeOnly in the two composition roots only, never on a test class path. The loader is a snapshot, so the
            // Sonatype snapshot repository is declared for net.luckperms only (see build.gradle.kts). Any bump needs owner approval.
            version("luckperms-api", "5.5")
            version("luckperms-minestom-loader", "5.6-SNAPSHOT")
            library("luckperms.api", "net.luckperms", "api").versionRef("luckperms-api")
            library("luckperms.minestom.loader", "net.luckperms", "minestom-loader").versionRef("luckperms-minestom-loader")

            // Guava and failureaccess: the LuckPerms loader needs them on the runtime class path and Minestom does not bring
            // them (spike 1.1, design.md risk R3). Pinned to the version Cygnus uses.
            version("guava", "33.7.2-jre")
            library("guava", "com.google.guava", "guava").versionRef("guava")

            bundle("hibernate", listOf("hibernate.core", "hibernate.hikaricp"))
            bundle("flyway", listOf("flyway.core", "flyway.mysql"))
            bundle("fawe", listOf("fawe.core", "fawe.bukkit"))

            plugin("run-paper", "xyz.jpenilla.run-paper").versionRef("run-paper")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
            plugin("plugin-yml", "net.minecrell.plugin-yml.paper").versionRef("plugin-yml")
        }
    }
}

include("legacy:shared:conversation-api")
include("legacy:shared:database")
include("legacy:shared:common")
include("legacy:shared:spline")
include("legacy:plugins:game")
include("legacy:plugins:setup")
include("legacy:server")

// Greenfield rebuild — see docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md
include("voyager:api")
include("voyager:physics")
include("voyager:race")
include("voyager:platform")
include("voyager:server")
include("voyager:setup")
include("voyager:fitness")

// Tooling that is not part of the rebuild's module graph.
include("tools:trace-recorder")
include("tools:map-converter")
