plugins {
    id("voyager.java-conventions")
}

// The artifact keeps the name it had before the module moved under voyager/: the project name is now the
// last path segment alone, so the base name is pinned rather than left to derive "api".
base {
    archivesName = "voyager-api"
}
