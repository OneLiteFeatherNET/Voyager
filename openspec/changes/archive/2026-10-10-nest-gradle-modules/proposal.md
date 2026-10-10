# Proposal

## Why

The repository root holds seven rebuild modules (`voyager-*`) next to the old tree (`server`, `plugins/*`,
`shared/*`) and the tooling (`tools/*`). Two trees in one flat list make it unclear which modules are being
replaced, and the old tree's paths are about to be deleted in one cut (the rebuild's E7). Nesting the rebuild
under `voyager/` and the old tree under `legacy/` makes the boundary visible in every Gradle path, in the file
tree and in the IDE, and leaves `tools/` and `buildSrc/` where they are.

## What Changes

- The rebuild modules move into a `voyager/` parent, with Gradle paths `:voyager:api`, `:voyager:physics`,
  `:voyager:race`, `:voyager:platform`, `:voyager:server`, `:voyager:setup` and `:voyager:fitness`. Each keeps its
  artifact and JAR name (`voyager-api`, `voyager-server`, ...) through `base { archivesName = ... }`.
- The old tree moves into a `legacy/` parent, with Gradle paths `:legacy:server`, `:legacy:plugins:game`,
  `:legacy:plugins:setup` and `:legacy:shared:*`. Its artifact and JAR names do not change.
- Every reference that names a moved path is updated: `settings.gradle.kts`, typesafe project accessors,
  `project(...)` dependencies, task paths in documentation, CI and Docker, relative paths in build scripts and in
  test and main code, the fitness coverage map, and the active documentation.
- The ignored and untracked run directories keep resolving to the same physical directories. `run/` and
  `run-setup/` stay at the root. `legacy/plugins/setup/run/` (the Paper test server, untracked) moves with its module.
- `.gitignore` gains the IDE, tool-output and run-state entries listed in the change's design, in its own commit.
- **BREAKING** for developers and CI: Gradle task paths change. `:server:shadowJar` becomes `:legacy:server:shadowJar`,
  `:voyager-server:runServer` becomes `:voyager:server:runServer`, and the same rule applies to every other path.
  Artifact and JAR names do not change.

Out of scope: no code behaviour change, no artifact rename, no deletion of the old tree, no edits to archived
changes, to ADR or research texts beyond broken links, or to `docker/mariadb/mariadb` (owned by another user).

Vertical slices touched: none. This is a build layout change across every module and does not touch any slice.

Conventional Commits: the change ships under the type `build` with no scope, as the owner title
`build: nest the rebuild under voyager/ and the old tree under legacy/`. The `.gitignore` commit (`chore(git)`) and
the documentation commit (`docs`) are separate commits of their own types, not a second change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `catalog/file-schema`: the schema directory and the shipped files' `$schema` paths move under `voyager/platform`
  and `voyager/server`; the `$id` URL follows the schema file to its new location.
- `server/config-check-mode`: the Gradle task path that runs the check changes from `:voyager-server:validateCatalog`
  to `:voyager:server:validateCatalog`.
- `cup-session-characterization`: the committed golden file path moves from `voyager-server/src/test/resources/...`
  to `voyager/server/src/test/resources/...`; the file bytes do not change.

## Impact

- `settings.gradle.kts`, every module `build.gradle.kts`, `buildSrc` (no path change there), `tools/*` dependencies.
- `voyager-fitness` coverage map and the build's `voyager.modulesWithSources` / `voyager.sourceRoots` properties,
  which now derive from the project path.
- Test and main code that reads a path from the source tree: `FileSchemaTest`, `ConverterOptionsTest`, the
  shipped map and cup JSON `$schema` values, and documentation comments that name `voyager-server/src/...`.
- CI: `.github/workflows/build.yml` (artifact path), `.github/workflows/release-please.yml` (`:legacy:server:shadowJar`
  and the release asset path), `docker/server/Dockerfile` (`:legacy:server:shadowJar` and the copy path).
- Documentation: `CLAUDE.md`, `docs/**` (active pages), `.claude/skills` and `.claude/commands` instructions,
  `openspec/config.yaml` context. Archived changes, ADR and research history keep their texts.
- No runtime behaviour, no ArchUnit rule, no frozen ArchUnit store content, no Golden Master byte changes.
