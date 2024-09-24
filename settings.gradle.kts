rootProject.name = "Voyager"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("paper", "1.21.1-R0.1-SNAPSHOT")

            library("minecraft.paper","io.papermc.paper", "paper-api").versionRef("paper")
        }
    }
}

include("shared:conversation-api")
include("shared:phase")
include("shared:common")
include("plugins:game")