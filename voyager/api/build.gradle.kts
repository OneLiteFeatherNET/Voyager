plugins {
    id("voyager.java-conventions")
    `java-library`
}

// The artifact keeps the name it had before the module moved under voyager/: the project name is now the
// last path segment alone, so the base name is pinned rather than left to derive "api".
base {
    archivesName = "voyager-api"
}

dependencies {
    // Catalogue identifiers are Adventure keys (ADR-0040). Only the key package is used here, and the ArchUnit rule in
    // voyager/fitness keeps it that way. The version is the one the platform resolves through adventure-api.
    api("net.kyori:adventure-key:5.2.0")
}
