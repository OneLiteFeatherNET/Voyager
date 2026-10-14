plugins {
    id("voyager.java-conventions")
}

// The artifact keeps the voyager- prefix of the other rebuild modules; the project name is the last path segment alone.
base {
    archivesName = "voyager-persistence"
}

dependencies {
    implementation(project(":voyager:api"))
    // Minestom and the platform log through slf4j; the writer's warnings go through the same facade.
    compileOnly("org.slf4j:slf4j-api:2.0.19")
}
