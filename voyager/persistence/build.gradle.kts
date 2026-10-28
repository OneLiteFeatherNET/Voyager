plugins {
    id("voyager.java-conventions")
}

// The artifact keeps the voyager- prefix of the other rebuild modules; the project name is the last path segment alone.
base {
    archivesName = "voyager-persistence"
}

dependencies {
    implementation(project(":voyager:api"))
    implementation(libs.hibernate.core)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    // Flyway and the JDBC driver find their PostgreSQL support at runtime; nothing here names those classes.
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    // Minestom and the platform log through slf4j; the writer's warnings go through the same facade.
    compileOnly("org.slf4j:slf4j-api:2.0.20")
}

// Integration tests start real PostgreSQL containers. They have their own source set so that `test` stays free of
// Docker, and `check` runs them after the unit tests (design D10).
val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
    // The clock and log fakes of the unit tests are reused, so an outage test drives the same backoff as the unit tests.
    compileClasspath += sourceSets.test.get().output
    runtimeClasspath += sourceSets.test.get().output
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.getByName("testImplementation"))
}

configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
}

dependencies {
    "integrationTestCompileOnly"(libs.jetbrains.annotations)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.testcontainers.junit.jupiter)
    "integrationTestRuntimeOnly"(libs.postgresql)
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the integration tests against PostgreSQL containers; they need Docker unless the guard skips them."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    // CI passes -Pvoyager.requireDocker=true so that a missing Docker daemon fails the suite instead of skipping it.
    systemProperty("voyager.requireDocker", providers.gradleProperty("voyager.requireDocker").getOrElse("false"))
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(integrationTestTask)
}
