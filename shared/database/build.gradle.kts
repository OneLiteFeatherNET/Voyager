plugins {
    java
}
dependencies {
    implementation(libs.bundles.hibernate)
    implementation(libs.mariadb)
    implementation(libs.jetbrains.annotations)
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }
}

