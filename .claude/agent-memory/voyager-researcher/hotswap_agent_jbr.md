---
name: HotswapAgent + JBR 25 integration
description: Verified Maven coordinates, JVM flags, and Gradle pattern for HotswapAgent 2.0.3 with JetBrains Runtime 25
type: reference
---

# HotswapAgent 2.0.3 + JBR 25

**Status:** Current (verified 2026-04-24)

## Core facts

- **Maven Central GAV:** `org.hotswapagent:hotswap-agent:2.0.3` — fatjar "all-in-one" with core + all plugins [T2-official, Maven Central]
- **Release date:** 2026-01-22 [T2-official]
- **Java versions:** Java 21+ (21 and 25 supported via JBR) [T2-official]
- **Plain OpenJDK:** Only supports method-body changes. Structural changes (new methods/fields/classes) REQUIRE JBR (DCEVM built-in). [T2-official, hotswapagent.org]

## JVM flags (Java 21/25 with JBR)

```
-XX:+AllowEnhancedClassRedefinition
-XX:HotswapAgent=external
-javaagent:/path/to/hotswap-agent-2.0.3.jar
```

Alternative: `-XX:HotswapAgent=fatjar` — uses the JAR placed at `<JBR>/lib/hotswap/hotswap-agent.jar` (plain filename, no version). The `external` + `-javaagent:` combo is preferred for Gradle-managed workflows (agent path comes from dependency resolution).

Optional: `-Xlog:redefine+class*=info` for reload visibility.

## JBR 25

- Latest release: 25.0.2-b329.72 (2026-03-03) and newer in the b329.x series
- Downloads: https://github.com/JetBrains/JetBrainsRuntime/releases
- CDN pattern: `https://cache-redirector.jetbrains.com/intellij-jbr/jbr-25.0.2-linux-x64-b329.<build>.tar.gz`
- Gradle toolchain: `JvmVendorSpec.JETBRAINS` (Gradle 8.4+) with `foojay-resolver-convention` auto-downloads JBR
- **Caveat:** Foojay always resolves the latest JBR; cannot pin exact build number via toolchain API

## Gradle pattern (detached configuration)

```kotlin
val hotswapAgent: Configuration by configurations.creating
dependencies {
    hotswapAgent("org.hotswapagent:hotswap-agent:2.0.3")
}

tasks.register<JavaExec>("runServerHotswap") {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "net.elytrarace.server.VoyagerServer"
    jvmArgs(
        "-XX:+AllowEnhancedClassRedefinition",
        "-XX:HotswapAgent=external",
        "-javaagent:${hotswapAgent.singleFile.absolutePath}",
    )
}
```

Requires `settings.gradle.kts` to apply `org.gradle.toolchains.foojay-resolver-convention`.

## Negative space

- No official HotswapAgent Gradle plugin exists. Community options: `com.ryandens.javaagent-application` (generic javaagent helper, not HotswapAgent-specific).
- HotswapAgent docs are Maven-only; Gradle integration is user-assembled.
- Class-hierarchy changes (changing superclass, adding/removing interfaces) are NOT supported even with DCEVM.
