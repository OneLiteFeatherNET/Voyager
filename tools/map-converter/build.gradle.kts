plugins {
    id("voyager.java-conventions")
    application
}

dependencies {
    // The converter builds real MapDefinition, Ring and CupDefinition values rather than plain
    // JSON objects, so every invariant voyager-api enforces — rings indexed 0..n-1, a unit-length
    // normal, a positive radius and reference time — is checked while the data is being converted
    // rather than the first time a server tries to read it. The tool depends on the rebuild's api
    // module; nothing in the rebuild depends on the tool.
    implementation(project(":voyager:api"))

    // Pinned inline, same reasoning as tools/trace-recorder/build.gradle.kts: a catalog alias for a
    // coordinate that other modules also resolve gives Renovate two pins to reconcile.
    implementation("com.google.code.gson:gson:2.14.0")
}

application {
    mainClass = "net.elytrarace.tools.converter.MapConverter"
}
