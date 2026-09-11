import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    id("voyager.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.plugin.yml)
}

// No repositories { } block here: the root build.gradle.kts already adds repo.papermc.io to every
// subproject via `subprojects { repositories { ... } }`. Re-declaring it was dead weight carried
// over from the brief's snippet.

dependencies {
    // Pinned inline, not through the catalog: renovate.json inherits
    // github>OneLiteFeatherNET/renovate:paper, which already matches io.papermc.paper:paper-api by
    // coordinate. A second catalog alias for the same artifact would give that rule two
    // incompatible version formats (1.21.5-R0.1-SNAPSHOT vs 26.2.build.123-stable) to reconcile,
    // and a Gradle version conflict resolves to the higher one — silently compiling the tree being
    // replaced against 26.2.
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
    // compileOnly, not implementation: Paper 26.2 bundles gson 2.14.0 itself (see
    // paper-spike/libraries/com/google/code/gson), so shading it into the plugin jar unrelocated
    // only inflates the artifact for a class that is already on the runtime classpath.
    compileOnly("com.google.code.gson:gson:2.14.0")
    // compileOnly is not inherited by the test source set, and TraceFileTest exercises real
    // Gson round-trips (there is no Paper server on the test classpath to provide it instead).
    testImplementation("com.google.code.gson:gson:2.14.0")
}

paper {
    main = "net.elytrarace.tools.recorder.RecorderPlugin"
    apiVersion = "1.21"
    authors = listOf("Voyager")
    permissions {
        register("trace-recorder.record") {
            description = "Allows flying a scripted glide profile and writing its trace fixture to disk."
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}
