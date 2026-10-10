plugins {
    id("voyager.java-conventions")
    `java-library`
}

dependencies {
    api(project(":voyager:api"))

    // Test-only: the trace replay harness (net.elytrarace.voyager.physics.trace) reads E2a's
    // fixture JSON format. Fixture reading is not production behaviour, so Gson never reaches
    // main — which is no longer a promise: ApiPurityTest's onlyPlatformDependsOnGson makes a Gson
    // import in this module's main sources a build failure, and it analyses production classes
    // only, so this line stays legal. Same reasoning as the java.nio.file rule beside it.
    testImplementation("com.google.code.gson:gson:2.14.0")
}

// The artifact keeps the name it had before the module moved under voyager/: the project name is now the
// last path segment alone, so the base name is pinned rather than left to derive "physics".
base {
    archivesName = "voyager-physics"
}
