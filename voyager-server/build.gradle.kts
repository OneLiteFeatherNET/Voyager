plugins {
    id("voyager.java-conventions")
}

// io.airlift:guice, not upstream com.google.inject:guice: the maintained fork carries no ASM and no
// Unsafe. Package names are unchanged (com.google.inject.*), so ApiPurityTest's DI rule matches on
// that package regardless of which artifact supplied it. Pinned inline for the same reason Minestom
// 26.2 is pinned inline in voyager-platform/build.gradle.kts — there is no existing catalog alias to
// collide with here, but a bare version string keeps the pin visible at the point of use rather than
// buried in a catalog only this one module reads.
val guiceVersion = "10"

dependencies {
    implementation(project(":voyager-api"))
    implementation(project(":voyager-physics"))
    implementation(project(":voyager-race"))
    implementation(project(":voyager-platform"))

    implementation("io.airlift:guice:$guiceVersion")
}
