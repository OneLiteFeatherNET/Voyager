plugins {
    id("voyager.java-conventions")
}

// The Minestom extension that CloudNet's bridge loads from a service's extensions/ folder (ADR-0026). It is a plain jar
// that is never bundled into a root's fat jar: the bridge has to load in its own class loader, beside the node's CloudNet.
//
// Everything it compiles against is provided at runtime. CloudNet is provided by the node, the CloudNet_Bridge extension
// provides the bridge classes, and Minestom and Adventure come from the root's class loader. So each is compileOnly, and
// nothing here reaches voyager-platform, voyager-server or voyager-setup (CloudNetBoundaryTest).
dependencies {
    // @ExtensionInfo generates extension.json at compile time. Its annotation has source retention, so it never reaches the jar.
    // The Extension base class is in the fork's jar (net.minestom.server.extensions), which the root provides at run time.
    annotationProcessor(platform(libs.minestom.extensions.bom))
    annotationProcessor(libs.minestom.extensions.processor)
    compileOnly(platform(libs.minestom.extensions.bom))
    compileOnly(libs.minestom.extensions)
    compileOnly(libs.minestom.extensions.processor)

    // Pinned to the rebuild's Minestom, inline, as voyager-platform pins it. Two pins of one artifact must agree, so the
    // version is the same string as voyager/platform/build.gradle.kts.
    compileOnly("net.minestom:minestom:2026.10.05-26.2")

    // CloudNet 4.0.0-RC17, the release the owner's network runs (see settings.gradle.kts and ADR-0026).
    compileOnly(platform(libs.cloudnet.bom))
    compileOnly(libs.cloudnet.bridge.api)
    compileOnly(libs.cloudnet.bridge.impl)
    compileOnly(libs.cloudnet.driver.api)

    // The unit tests use Minestom's and Adventure's types for the pointer, and nothing from CloudNet.
    testImplementation("net.minestom:minestom:2026.10.05-26.2")
}

// The extension processor reads the project version, which subprojects do not inherit from the root (as Cygnus does).
tasks.compileJava {
    options.compilerArgs.add("-Aminestom.extension.version=${project.version}")
}

// The jar keeps the name the node's extensions/ folder expects: voyager-cloudnet-bridge-<version>.jar.
base {
    archivesName = "voyager-cloudnet-bridge"
}
