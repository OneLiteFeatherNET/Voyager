rootProject.name = "Voyager"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("paper", "1.21.5-R0.1-SNAPSHOT")
            version("hibernate", "7.3.0.Final")
            version("mariadb-client", "3.5.7")
            version("jetbrains-annotations", "26.1.0")
            version("fawe-bom", "1.55")
            version("commons-geometry-euclidean", "1.0")
            version("run-paper", "3.0.2")
            version("shadow", "9.4.0")
            version("plugin-yml", "0.6.0")

            library("minecraft.paper","io.papermc.paper", "paper-api").versionRef("paper")
            library("minecraft.cloud.paper", "org.incendo", "cloud-paper").version("2.0.0-SNAPSHOT")
            library("hibernate.core", "org.hibernate.orm", "hibernate-core").versionRef("hibernate")
            library("hibernate.hikaricp", "org.hibernate.orm", "hibernate-hikaricp").versionRef("hibernate")
            library("mariadb", "org.mariadb.jdbc", "mariadb-java-client").versionRef("mariadb-client")
            library("jetbrains.annotations", "org.jetbrains", "annotations").versionRef("jetbrains-annotations")
            library("fawe.bom", "com.intellectualsites.bom","bom-newest").versionRef("fawe-bom")
            library("fawe.core", "com.fastasyncworldedit", "FastAsyncWorldEdit-Core").withoutVersion()
            library("fawe.bukkit", "com.fastasyncworldedit", "FastAsyncWorldEdit-Bukkit").withoutVersion()
            library("geometry", "org.apache.commons", "commons-geometry-euclidean").versionRef("commons-geometry-euclidean")

            bundle("hibernate", listOf("hibernate.core", "hibernate.hikaricp"))
            bundle("fawe", listOf("fawe.core", "fawe.bukkit"))

            plugin("run-paper", "xyz.jpenilla.run-paper").versionRef("run-paper")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
            plugin("plugin-yml", "net.minecrell.plugin-yml.paper").versionRef("plugin-yml")
        }
    }
}

include("shared:conversation-api")
include("shared:phase")
include("shared:database")
include("shared:common")
include("plugins:game")
include("plugins:setup")