plugins {
    `java`
}
dependencies {
    compileOnly(libs.minecraft.paper)
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

