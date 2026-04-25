plugins {
    java
}
dependencies {
    implementation(libs.bundles.hibernate)
    implementation(libs.mariadb)
    implementation(libs.jetbrains.annotations)
    implementation(project(":shared:common"))

    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
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

