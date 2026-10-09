# Proposal

**Conventional Commits:** `feat(build)`. Squash-merge PR title: `feat(build): check maps, cups and worlds together before the server starts`.

Slices: `catalog` (ring 3, `voyager-platform`), `world` (ring 3, `voyager-platform`), `server.config` (composition root, `voyager-server`), and the run tasks in `voyager-server/build.gradle.kts`.

**Depends on, in this merge order:**
1. `fix-run-data-sync` (`fix(build)`). The check reads `run/run/data`, which `prepareRunData` only copies into today. Until the stale-file fix lands, a map deleted from `src/main/resources` is still reported valid.
2. `unify-catalog-loading`. It supplies `CatalogLoader.read`, which returns every catalogue problem as data (`CatalogReading`). This change owns the all-at-once report built on it.
3. `name-both-cup-switches` only for the wording of the settings problems it names (no code dependency).

Roadmap Q4 (`scope-cup-validation`) is related and not required. Its rule (the selected cup is what must be consistent) follows whatever Q4 decides once it lands; until then, every cup is validated as an error.

## Why

NFR-007 requires that every configuration error is reported together and that the server can validate and exit without binding a socket. Neither exists in source (research 005, P10 and E3). Today an operator learns of one problem per boot: a missing world directory stops `ServerSettings` at the first missing path, a bad map file stops the catalogue at the first bad file, and a missing world surfaces only when a map is first entered. A run task that merely warns about a missing worlds directory and then starts the server is the same failure in a different place.

## What Changes

- Add a validate-and-exit mode to the server: `-Dvoyager.config.check=true` loads the catalogue, checks every referenced world (folder, region data, and the deep world health check), checks the resolved settings, prints every problem with its file and field, and exits 0 when there are no errors or 1 when there is at least one. It never binds a socket and never starts the game server.
- Add the Gradle task `:voyager-server:validateCatalog`, which runs that mode against the same directories the run tasks use (`run/run/data` and `run/run/worlds`, or the `-PdataPath` and `-PworldsPath` overrides).
- Make `runServer` and `runServerDev` depend on `validateCatalog`, so a run with errors stops before the JVM starts the server. **BREAKING (developer workflow):** a run with a missing worlds directory or an invalid catalogue now fails at the validation step instead of logging a warning and starting the server.
- Add the skip switch `-PskipCatalogCheck` for the two run tasks (approved by the owner on 2026-10-09). It skips only the run-task dependency. `validateCatalog` itself always runs when asked for, and a skipped run prints one warning line saying the check was skipped.
- Add `ConfigProblem(key, source, message, severity)` to `voyager-api` (the record NFR-007 names). Every problem the check reports is one `ConfigProblem`, sorted so that two runs on two machines print the same lines.
- **The all-at-once report is owned here.** The catalogue half collects every problem from `CatalogLoader.read` (unify-catalog-loading) and turns each into a `ConfigProblem`. The normal boot path also refuses with this same full report (the owner allows this in this change), so an operator sees every error at boot, not one per restart.
- **Deep world check (approved 2026-10-09, was Could):** for every map, `WorldHealth.isSound()` (from `MapInstances.healthOf`, Falco-backed) is part of `validateCatalog`. It needs no Minestom client and no connected player; it does need Minestom's registries initialised. Its runtime cost is recorded in `design.md` and measured before the PR opens.
- Report, for every map, whether its `world` folder exists under the worlds directory and holds region data, using the same layout rule `MapInstances` applies (`region/` or `dimensions/`), extracted into a Minestom-free `WorldFolders` so both callers share it.
- Report settings problems (unknown or unreadable data and worlds directories, a non-numeric or out-of-range port, a `VOYAGER_CUP` naming a cup that does not exist) without throwing on the first one. `ServerSettings`' compact constructor keeps its refusal for the normal boot path.
- Out of scope: scoped cup validation (Q4, `scope-cup-validation`), hot reload (Q7), the setup slice (`voyager-setup`, not in the build), and the tree being replaced.
- The doubled `run/run` path is kept unchanged. The check prints absolute paths so the doubling is visible in every report.

## Capabilities

### New Capabilities
- `catalog/catalog-validation`: checking that the catalogue and the worlds it references are complete and consistent, reporting every problem with its file and field, and failing as one unit.
- `server/config-check-mode`: the validate-and-exit run mode, its exit codes, its Gradle task, the skip switch, and the run tasks that depend on it.

### Modified Capabilities
- None. `openspec/specs/` holds no requirement on configuration or catalogues today.

## Impact

- **Code (api):** `ConfigProblem` record in a new `net.elytrarace.voyager.api.config` package with `package-info.java`.
- **Code (platform):** `CatalogValidation` in `net.elytrarace.voyager.platform.catalog`, built on `CatalogLoader.read` from `unify-catalog-loading` and on `MapInstances.healthOf` for the deep check; `WorldFolders` in `net.elytrarace.voyager.platform.world`, extracted from `MapInstances.holdsRegionData`.
- **Code (server):** `ConfigCheck` in `net.elytrarace.voyager.server.config` (non-throwing settings checks plus the catalogue and world results); a `main` branch in `VoyagerServer` for `voyager.config.check`; the normal boot refusal formats the same report.
- **Build:** `voyager-server/build.gradle.kts` gains `validateCatalog` (`JavaExec`, `dependsOn(prepareRunData)`); both run tasks depend on it unless `-PskipCatalogCheck` is set. The `doFirst` missing-worlds warning is replaced by the validator's error.
- **Fitness:** no new ArchUnit rule is proposed. `ApiPurityTest` already covers `ConfigProblem`.
- **Docs:** `docs/reference` gets the check's exit codes, output format, skip switch and runtime cost; research 005 Q6 and P10 status are updated in the same PR as the code.
- **Runtime:** none on a valid configuration beyond the check's own time (see design, decision 3). A run with problems stops earlier and prints the full report.
