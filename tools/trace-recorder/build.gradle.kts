plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.minecraft.paper.api)
    implementation("com.google.code.gson:gson:2.14.0")
}

paper {
    main = "net.elytrarace.tools.recorder.RecorderPlugin"
    apiVersion = "1.21"
    authors = listOf("Voyager")
}
