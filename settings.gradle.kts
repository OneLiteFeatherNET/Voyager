rootProject.name = "Voyager"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("paper", "1.21.5-R0.1-SNAPSHOT")
            version("minestom", "2026.04.13-1.21.11")
            version("hibernate", "7.3.0.Final")
            version("flyway", "10.21.0")
            version("mariadb-client", "3.5.7")
            version("jetbrains-annotations", "26.1.0")
            version("fawe-bom", "1.56")
            version("commons-geometry-euclidean", "1.0")
            version("archunit", "1.4.2")
            version("run-paper", "3.0.2")
            version("shadow", "9.4.1")
            version("plugin-yml", "0.6.0")

            library("minecraft.paper","io.papermc.paper", "paper-api").versionRef("paper")
            library("minecraft.minestom", "net.minestom", "minestom").versionRef("minestom")
            library("minecraft.minestom.testing", "net.minestom", "testing").versionRef("minestom")
            library("minecraft.cloud.paper", "org.incendo", "cloud-paper").version("2.0.0-SNAPSHOT")
            library("minecraft.cloud.minestom", "org.incendo", "cloud-minestom").version("2.0.0-SNAPSHOT")

            // OneLiteFeather Libraries (via aonyx-bom)
            version("aonyx-bom", "0.7.0")
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

            // Logging — Log4j2 as SLF4J 2.x provider (Minestom ships SLF4J 2.x API)
            version("log4j2", "2.25.4")
            library("log4j2.core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j2")
            library("log4j2.slf4j2", "org.apache.logging.log4j", "log4j-slf4j2-impl").versionRef("log4j2")

            bundle("hibernate", listOf("hibernate.core", "hibernate.hikaricp"))
            bundle("flyway", listOf("flyway.core", "flyway.mysql"))
            bundle("fawe", listOf("fawe.core", "fawe.bukkit"))

            plugin("run-paper", "xyz.jpenilla.run-paper").versionRef("run-paper")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
            plugin("plugin-yml", "net.minecrell.plugin-yml.paper").versionRef("plugin-yml")
        }
    }
}

include("shared:conversation-api")
include("shared:database")
include("shared:common")
include("shared:spline")
include("plugins:game")
include("plugins:setup")
include("server")