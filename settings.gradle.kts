rootProject.name = "Voyager"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("paper", "1.21.5-R0.1-SNAPSHOT")
            version("minestom", "2026.05.11-1.21.11")
            version("hibernate", "7.3.11.Final")
            version("flyway", "12.10.0")
            version("mariadb-client", "3.5.10")
            version("jetbrains-annotations", "26.1.0")
            version("fawe-bom", "1.56")
            version("commons-geometry-euclidean", "1.0")
            version("archunit", "1.5.1")
            version("run-paper", "3.0.2")
            version("shadow", "9.4.3")
            version("plugin-yml", "0.6.0")

            library("minecraft.paper","io.papermc.paper", "paper-api").versionRef("paper")
            library("minecraft.minestom", "net.minestom", "minestom").versionRef("minestom")
            library("minecraft.minestom.testing", "net.minestom", "testing").versionRef("minestom")
            library("minecraft.cloud.paper", "org.incendo", "cloud-paper").version("2.0.1")
            library("minecraft.cloud.minestom", "org.incendo", "cloud-minestom").version("2.0.0-SNAPSHOT")

            // OneLiteFeather Libraries (via aonyx-bom)
            version("aonyx-bom", "0.8.9")
            library("aonyx.bom", "net.onelitefeather", "aonyx-bom").versionRef("aonyx-bom")
            // The tree being replaced stays on the aonyx-bom it was built against. Its Minestom is 2026.05.11-1.21.11,
            // and aonyx-bom 0.8.9 imports mycelium-bom 1.8.9, which would move it to 26.2. Never upgrade it here.
            version("aonyx-bom-legacy", "0.7.1")
            library("aonyx.bom.legacy", "net.onelitefeather", "aonyx-bom").versionRef("aonyx-bom-legacy")
            // Pica, the dialog library the tutorial book uses (ADR-0039, openspec change cygnus-style-lobby). 0.1.6 is the version
            // Cygnus uses. Its POM depends on Minestom 2026.10.07-26.2, the version aonyx-bom 0.8.9 resolves, and resolves from
            // repo.onelitefeather.dev/releases without credentials. Pinned here because aonyx-bom 0.8.9 cannot be read anonymously.
            version("pica", "0.1.6")
            library("pica", "net.onelitefeather", "pica").versionRef("pica")
            library("aves", "net.theevilreaper", "aves").withoutVersion()
            library("xerus", "net.theevilreaper", "xerus").withoutVersion()
            library("hibernate.core", "org.hibernate.orm", "hibernate-core").versionRef("hibernate")
            library("hibernate.hikaricp", "org.hibernate.orm", "hibernate-hikaricp").versionRef("hibernate")
            library("flyway.core", "org.flywaydb", "flyway-core").versionRef("flyway")
            library("flyway.mysql", "org.flywaydb", "flyway-mysql").versionRef("flyway")

            // PostgreSQL statistics store (openspec change player-stats, design D1, ADR-0037). Each version was on Maven Central
            // for at least two weeks on 2026-10-12, the date the versions were checked; newer releases inside that window are not used.
            // HikariCP 7.1.0, uploaded 2026-06-14: the pool, used directly so Flyway and the session factory share it.
            version("hikaricp", "7.1.0")
            library("hikaricp", "com.zaxxer", "HikariCP").versionRef("hikaricp")
            // Flyway's PostgreSQL support, required since Flyway 10; same release as flyway-core, uploaded 2026-06-30.
            library("flyway.postgresql", "org.flywaydb", "flyway-database-postgresql").versionRef("flyway")
            // JDBC driver, uploaded 2026-07-06. 42.7.14 (uploaded 2026-10-07) is inside the window and not used yet.
            version("postgresql", "42.7.13")
            library("postgresql", "org.postgresql", "postgresql").versionRef("postgresql")
            // Testcontainers for the integration source set, uploaded 2026-04-20.
            version("testcontainers", "2.0.5")
            library("testcontainers.postgresql", "org.testcontainers", "testcontainers-postgresql").versionRef("testcontainers")
            library("testcontainers.junit.jupiter", "org.testcontainers", "testcontainers-junit-jupiter").versionRef("testcontainers")
            library("mariadb", "org.mariadb.jdbc", "mariadb-java-client").versionRef("mariadb-client")
            library("jetbrains.annotations", "org.jetbrains", "annotations").versionRef("jetbrains-annotations")
            library("fawe.bom", "com.intellectualsites.bom","bom-newest").versionRef("fawe-bom")
            library("fawe.core", "com.fastasyncworldedit", "FastAsyncWorldEdit-Core").withoutVersion()
            library("fawe.bukkit", "com.fastasyncworldedit", "FastAsyncWorldEdit-Bukkit").withoutVersion()
            library("geometry", "org.apache.commons", "commons-geometry-euclidean").versionRef("commons-geometry-euclidean")
            library("archunit.junit5", "com.tngtech.archunit", "archunit-junit5").versionRef("archunit")

            // JSON Schema 2020-12 validator, test scope only (voyager-platform): it checks the committed map and
            // cup files and the test fixtures against schema/*.schema.json. It is never on a main classpath, so
            // the loader stays the only runtime check. Pinned to 3.0.7, released 2026-08-20. 3.0.8 was released
            // 2026-09-30, inside the two-week cooling window before 2026-10-09, so it is not used yet.
            // Approved by the owner on 2026-10-09 (openspec change simplify-map-data-format, design decision 5).
            version("json-schema-validator", "3.0.8")
            library("json.schema.validator", "com.networknt", "json-schema-validator").versionRef("json-schema-validator")

            version("junit", "6.1.2")
            version("assertj", "3.27.7")
            library("junit.bom", "org.junit", "junit-bom").versionRef("junit")
            library("junit.jupiter", "org.junit.jupiter", "junit-jupiter").withoutVersion()
            library("assertj", "org.assertj", "assertj-core").versionRef("assertj")

            // Logging — Log4j2 as SLF4J 2.x provider (Minestom ships SLF4J 2.x API)
            version("log4j2", "2.26.0")
            library("log4j2.core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j2")
            library("log4j2.slf4j2", "org.apache.logging.log4j", "log4j-slf4j2-impl").versionRef("log4j2")

            // Compile-time DI for the composition root only (voyager-server). The processor generates the
            // wiring; no other module may depend on any of these or on jakarta.inject.
            version("avaje-inject", "12.7")
            library("avaje.inject.runtime", "io.avaje", "avaje-inject").versionRef("avaje-inject")
            library("avaje.inject.processor", "io.avaje", "avaje-inject-generator").versionRef("avaje-inject")
            library("avaje.inject.test", "io.avaje", "avaje-inject-test").versionRef("avaje-inject")
            library("jakarta.inject", "jakarta.inject", "jakarta.inject-api").version("2.0.1")

            // Server configuration (ADR-0036): the loader in voyager-platform builds one avaje-config Configuration. The same
            // release Titan runs in production. SnakeYAML is optional in avaje-config's POM and is what reads application.yaml
            // in the working directory; without it avaje-config falls back to a simpler parser that does not read every YAML.
            version("avaje-config", "5.2")
            library("avaje.config", "io.avaje", "avaje-config").versionRef("avaje-config")
            version("snakeyaml", "2.7")
            library("snakeyaml", "org.yaml", "snakeyaml").versionRef("snakeyaml")

            // LuckPerms, the optional permission backend behind the PermissionPolicy port (ADR-0024). The API is compileOnly
            // in voyager-platform. The Minestom loader is compileOnly there too, because LuckPermsBootstrap starts it, and
            // runtimeOnly in the two composition roots only, never on a test class path. Both are release artifacts of the
            // OneLiteFeather LuckPerms fork, resolved from repo.onelitefeather.dev/releases (declared in build.gradle.kts).
            // The loader must be 6.0.2 or newer: 6.0.1 calls ConnectionManager.getOnlinePlayers()Ljava/util/Collection;, which
            // Minestom 2026.10.07 removed (NoSuchMethodError on boot). Any bump needs owner approval.
            version("luckperms-api", "6.0.1")
            version("luckperms-minestom-loader", "6.0.2")
            library("luckperms.api", "net.luckperms", "api").versionRef("luckperms-api")
            library("luckperms.minestom.loader", "net.luckperms", "minestom-loader").versionRef("luckperms-minestom-loader")

            // Guava and failureaccess: the LuckPerms loader needs them on the runtime class path and Minestom does not bring
            // them (spike 1.1, design.md risk R3). Pinned to the version Cygnus uses.
            version("guava", "33.7.2-jre")
            library("guava", "com.google.guava", "guava").versionRef("guava")

            // CloudNet 4.0.0-RC17, the release the owner's network runs (ADR-0026). It was RC16 until 2026-10-11, the release Cygnus
            // builds against; the extension's API calls are identical in both (javap, design.md Decision 6). Only
            // :voyager:cloudnet-bridge compiles against it. It is never bundled: the node provides the driver, and the bridge arrives
            // as a Minestom extension from extensions/. The bom pins the rest.
            version("cloudnet", "4.0.0-RC17")
            library("cloudnet.bom", "eu.cloudnetservice.cloudnet", "bom").versionRef("cloudnet")
            library("cloudnet.bridge.api", "eu.cloudnetservice.cloudnet", "bridge-api").withoutVersion()
            library("cloudnet.bridge.impl", "eu.cloudnetservice.cloudnet", "bridge-impl").withoutVersion()
            library("cloudnet.driver.api", "eu.cloudnetservice.cloudnet", "driver-api").withoutVersion()

            // The OneLiteFeather fork of the Minestom extension loader, the release Cygnus pins. Minestom 26.2 has no extension
            // system of its own (greenfield design). The fork's ExtensionBootstrap is init(Auth) and start(host, port), see ADR-0026.
            // The processor generates extension.json from @ExtensionInfo at compile time; its annotation has source retention.
            version("minestom-extensions", "2.2.0")
            library("minestom.extensions.bom", "net.onelitefeather", "minestom-extensions-bom").versionRef("minestom-extensions")
            library("minestom.extensions", "net.onelitefeather", "minestom-extensions").withoutVersion()
            library("minestom.extensions.processor", "net.onelitefeather", "minestom-extensions-processor").withoutVersion()

            bundle("hibernate", listOf("hibernate.core", "hibernate.hikaricp"))
            bundle("flyway", listOf("flyway.core", "flyway.mysql"))
            bundle("fawe", listOf("fawe.core", "fawe.bukkit"))

            plugin("run-paper", "xyz.jpenilla.run-paper").versionRef("run-paper")
            plugin("shadow", "com.gradleup.shadow").versionRef("shadow")
            plugin("plugin-yml", "net.minecrell.plugin-yml.paper").versionRef("plugin-yml")
        }
    }
}

include("legacy:shared:conversation-api")
include("legacy:shared:database")
include("legacy:shared:common")
include("legacy:shared:spline")
include("legacy:plugins:game")
include("legacy:plugins:setup")
include("legacy:server")

// Greenfield rebuild — see docs/superpowers/specs/2026-09-09-voyager-greenfield-design.md
include("voyager:api")
include("voyager:persistence")
include("voyager:physics")
include("voyager:race")
include("voyager:platform")
include("voyager:server")
include("voyager:setup")
include("voyager:fitness")
include("voyager:cloudnet-bridge")

// Tooling that is not part of the rebuild's module graph.
include("tools:trace-recorder")
include("tools:map-converter")
