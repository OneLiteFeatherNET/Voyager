plugins {
    id("voyager.java-conventions")
}

// This module exists to give ArchUnit a classpath containing every module of the rebuild.
// The audit of the tree being replaced found architecture rules that silently checked nothing,
// because they ran on a classpath that did not contain the modules they named.
dependencies {
    testImplementation(project(":voyager-api"))
    testImplementation(libs.archunit.junit5)
}
