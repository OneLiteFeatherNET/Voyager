plugins {
    java
}
dependencies {
    compileOnly(libs.minecraft.paper)
    implementation(project(":shared:phase"))
    implementation(project(":shared:common"))
    implementation(project(":shared:database"))
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

