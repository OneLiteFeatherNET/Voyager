plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Pinned inline, not through the catalog: renovate.json inherits
    // github>OneLiteFeatherNET/renovate:paper, which already matches io.papermc.paper:paper-api by
    // coordinate. A second catalog alias for the same artifact would give that rule two
    // incompatible version formats (1.21.5-R0.1-SNAPSHOT vs 26.2.build.123-stable) to reconcile,
    // and a Gradle version conflict resolves to the higher one — silently compiling the tree being
    // replaced against 26.2.
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
    implementation("com.google.code.gson:gson:2.14.0")
}

paper {
    main = "net.elytrarace.tools.recorder.RecorderPlugin"
    apiVersion = "1.21"
    authors = listOf("Voyager")
}
