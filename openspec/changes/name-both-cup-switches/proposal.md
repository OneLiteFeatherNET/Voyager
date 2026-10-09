# Proposal

**Conventional Commits:** `fix(server)`. Squash-merge PR title: `fix(server): name the gradle property next to the system property in startup errors`.

## Why

Two startup errors in `voyager-server` tell the operator to set a switch in one form only. The cup error
(`UnresolvedCupException.ambiguous`) says `set -DVOYAGER_CUP=<name>`, and the missing-directory error
(`MissingServerDirectoryException`) says `set -D<VOYAGER_DATA_PATH|VOYAGER_WORLDS_PATH>=<dir>`. An operator who
starts the server through `./gradlew :voyager-server:runServer` has no `-D` to add to the JVM command line; the
Gradle form is `-Pcup`, `-PdataPath` or `-PworldsPath`, which `voyager-server/build.gradle.kts` maps onto the
same system properties. The error therefore names a switch that does not work in the way the operator runs
the server.

## What Changes

- The ambiguous-cup message names both forms: `set -Pcup=<name> (Gradle) or -DVOYAGER_CUP=<name> (java -jar)`.
- The missing-directory message names both forms for the matching purpose, for example
  `set -DVOYAGER_DATA_PATH=<dir> (java -jar) or -PdataPath=<dir> (Gradle)`.
- The `no cup named` message is unchanged: it names no switch.
- No property name, default, Gradle mapping or exit behaviour changes.

## Impact

- Slices: the cup slice (`game/exception/UnresolvedCupException`) and the config slice
  (`config/exception/MissingServerDirectoryException`). Both are domain exceptions and stay free of Gradle or
  Minestom imports; the Gradle names are plain strings in the message.
- Decisions: none altered. Design spec decision D2 (cup selection by `-DVOYAGER_CUP` or `-Pcup`) already names
  both forms; this change makes the error text match it.

## Out of Scope

- The retired tree (`server/src/main/java/net/elytrarace/server/VoyagerServer.java` javadoc and `server/build.gradle.kts`).
  It gets no new features and only its own startup text would need the same fix; not touched here.
- Renaming any property, adding a new switch, or changing `build.gradle.kts`.
- The `docs/research/005` findings (P11, Q5) are recorded there; this change does not edit research papers.
