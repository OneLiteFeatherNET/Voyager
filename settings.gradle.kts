rootProject.name = "Voyager"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("paper", "1.21.1-R0.1-SNAPSHOT")
            version("hibernate", "6.5.2.Final")
            version("mariadb-client", "3.4.1")
            version("jetbrains-annotations", "24.1.0")

            library("minecraft.paper","io.papermc.paper", "paper-api").versionRef("paper")
            library("hibernate.core", "org.hibernate.orm", "hibernate-core").versionRef("hibernate")
            library("hibernate.hikaricp", "org.hibernate.orm", "hibernate-hikaricp").versionRef("hibernate")
            library("mariadb", "org.mariadb.jdbc", "mariadb-java-client").versionRef("mariadb-client")
            library("jetbrains.annotations", "org.jetbrains", "annotations").versionRef("jetbrains-annotations")

            bundle("hibernate", listOf("hibernate.core", "hibernate.hikaricp"))
        }
    }
}

include("shared:conversation-api")
include("shared:phase")
include("shared:database")
include("shared:common")
include("plugins:game")