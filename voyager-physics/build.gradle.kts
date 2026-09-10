plugins {
    id("voyager.java-conventions")
    `java-library`
}

dependencies {
    api(project(":voyager-api"))

    // Test-only: the trace replay harness (net.elytrarace.voyager.physics.trace) reads E2a's
    // fixture JSON format. Fixture reading is not production behaviour, so Gson never reaches
    // main — see voyager-fitness's ApiPurityTest, which forbids java.nio.file there for the same
    // reason.
    testImplementation("com.google.code.gson:gson:2.14.0")
}
