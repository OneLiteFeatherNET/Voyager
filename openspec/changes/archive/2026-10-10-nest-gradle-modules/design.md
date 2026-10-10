# Design

## Dependency direction

Unchanged. The rings and module dependencies stay as `architecture/module-rings` defines them: `voyager-api` at the
centre, `voyager-physics` and `voyager-race` on it, `voyager-platform` on those, the composition roots on top, and
`voyager-fitness` outside the rings. Only the directories and Gradle paths move. Each module keeps its Java package
and its artifact name.

No architecture decision changes, so no MADR ADR is written. The layout decision is recorded in the owner's
decision list for this change and in the documentation commit.

## Move map

| Before | After | Gradle path | Artifact / JAR name |
|---|---|---|---|
| `voyager-api/` | `voyager/api/` | `:voyager:api` | `voyager-api` (pinned with `base.archivesName`) |
| `voyager-physics/` | `voyager/physics/` | `:voyager:physics` | `voyager-physics` (pinned) |
| `voyager-race/` | `voyager/race/` | `:voyager:race` | `voyager-race` (pinned) |
| `voyager-platform/` | `voyager/platform/` | `:voyager:platform` | `voyager-platform` (pinned) |
| `voyager-server/` | `voyager/server/` | `:voyager:server` | `voyager-server-<version>.jar` (pinned) |
| `voyager-setup/` | `voyager/setup/` | `:voyager:setup` | `voyager-setup` (pinned) |
| `voyager-fitness/` | `voyager/fitness/` | `:voyager:fitness` | none (test-only) |
| `server/` | `legacy/server/` | `:legacy:server` | `server-<version>.jar` (unchanged, project name stays `server`) |
| `plugins/game/` | `legacy/plugins/game/` | `:legacy:plugins:game` | unchanged |
| `plugins/setup/` | `legacy/plugins/setup/` | `:legacy:plugins:setup` | unchanged |
| `shared/{common,conversation-api,database,spline}/` | `legacy/shared/*/` | `:legacy:shared:*` | unchanged |

`tools/map-converter` and `tools/trace-recorder` stay at `tools/`. `buildSrc/` stays at the root.

A Gradle project takes its name from the last path segment. Without a pin, the rebuild's `api` would produce
`api-<version>.jar`, and `voyager-server` would produce `server-<version>.jar`, which collides with the old tree's
`server`. Each rebuild module therefore sets `base { archivesName = "voyager-<name>" }` explicitly. The old tree's
project names are the same as before, so its JAR names do not change. The rebuild's shadow JAR inherits the base name.

Gradle allows two projects with the same simple name at different paths (`:voyager:server` and `:legacy:server`,
`:voyager:setup` and `:legacy:plugins:setup`). Every reference uses the full path, and no task is selected by a bare name.

## Run directories

Nothing physical moves except the two directories inside the legacy plugin module. The `run` and `run-setup`
locations are resolved from the root or from the working directory as before, so their physical location is the same
before and after:

- `voyager/server`: `workingDir = rootProject.file("run")`, data at `run/run/data`, worlds at `run/run/worlds`. Unchanged.
- `voyager/fitness` integration world: `rootProject.file("run/run/worlds/ElytraraceBlueAndRed")`. Unchanged.
- `voyager/setup`: `workingDir = rootProject.projectDir`, defaults `run-setup/data` and `run-setup/worlds` at the root.
  Unchanged.
- `legacy/plugins/setup/run/`: the Paper run-paper directory relative to its project. It is untracked, so it is moved
  with its module (a directory rename), and its contents are verified before and after. Its `build/` directory moves with it.

The `.gitignore` entries therefore change from `/plugins/setup/run/` to `/legacy/plugins/setup/run/`.

## Paths that change in code and in test resources

Relative paths that are resolved from a module's working directory, and that name a sibling module, change by one
level of nesting:

- `voyager/platform` `FileSchemaTest`: `Path.of("..", "voyager-server", "src", "main", "resources")` becomes
  `Path.of("..", "server", "src", "main", "resources")`. The working directory is `voyager/platform`, so `..` is `voyager/`.
- `voyager/server` map and cup JSON `$schema` values: five levels up still reaches `voyager/`, so the value becomes
  `../../../../../platform/src/main/resources/schema/map.schema.json` (and `cup.schema.json`).
- `$id` of `map.schema.json` and `cup.schema.json`: the raw GitHub URL of the file, with `voyager-platform/` replaced by
  `voyager/platform/`.
- `tools/map-converter` `ConverterOptionsTest` and the usage comment in `MapConverter`: the example `--out` value
  becomes `voyager/server/src/main/resources`.
- Javadoc and comments that name `voyager-server/src/...` or the build task `:voyager-server:...`: updated, since they
  are used as navigation.

The ArchUnit store path (`src/test/resources/archunit_store`) and the `archunit.properties` path are relative to the
module's working directory, and the module keeps its own working directory. The store files contain only
`Foo.java:NN` source names, so they are not touched.

## Fitness coverage without the colon problem

`voyager-fitness/build.gradle.kts` used to select rebuild modules by the `voyager-` name prefix and pass the names
through a `File.pathSeparator` (`:`) property. Project paths now contain colons, so the selection uses the path
prefix `:voyager:`, and the property carries the path with `:` replaced by `/`. `FitnessCoverageTest` converts it back.
The map keys become `:voyager:api`, `:voyager:physics` and so on, and the test's message text names the same paths.
The test's logic and the set of covered modules stay the same.

## Typesafe accessors

Gradle generates `projects.voyager.api` (and the same shape for every module) and `projects.legacy.shared.common`.
Build scripts in this repository use `project(":...")` strings, so no accessor code changes are needed, but any
documentation that writes `projects.voyagerApi` is updated to `projects.voyager.api`.

## Ignore rules

The new `.gitignore` entries, in their own commit: `.idea/`, `*.iml`, `graphify-out/`,
`.claude/scheduled_tasks.lock`, `/legacy/plugins/setup/run/`, and `/docker/mariadb/mariadb/`, with a trailing newline.
`docker/mariadb/mariadb/` is owned by another user, so it is ignored and not touched. The root `run/` directory is
already ignored through its `/run/data/` and `/run/worlds/` entries and is not changed.

## Verification

- Test counts: a clean `CI=true ./gradlew clean build --continue` on `main` before the first move, then the same command
  after each move commit. Test XML counts (tests, failures, errors, skipped) must match.
- Golden Master: `CupSessionGoldenMasterTest` runs in the build, and the golden files must be byte-identical to
  `main` (checked with `git diff --quiet main -- '*/src/test/resources/golden/*'` after the moves, with the path prefix changed).
- Fitness: `CI=true ./gradlew :voyager:fitness:test` passes, and `git diff --quiet` on the ArchUnit store
  (`voyager/fitness/src/test/resources/archunit_store`) against `main` shows no content change.
- `./gradlew :voyager:server:validateCatalog -PworldsPath=/mnt/projects/oss/onelitefeather/Voyager/run/run/worlds` passes.
- Smoke: `./gradlew :voyager:server:runServer` until "Listening on", then kill the process tree, and port 25565 must be free.
  The same for `./gradlew :voyager:setup:runSetupDev` on port 25566.
- `./gradlew :legacy:server:shadowJar` produces `legacy/server/build/libs/server-1.12.0.jar`.

## Test discipline

This is a pure move, so Red/Green TDD has no new behaviour to drive. The existing suite and the fitness rules are the
gate. The only test code that changes is path literals in `FileSchemaTest`, `ConverterOptionsTest` and the fitness
coverage test's module keys, and each of those tests keeps its assertions. None of them adds a sleep, a clock read or
shared state.
