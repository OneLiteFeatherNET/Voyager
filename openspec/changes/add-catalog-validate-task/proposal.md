# Proposal

**Conventional Commits:** `feat(build)`. Squash-merge PR title: `feat(build): check maps, cups and worlds together before the server starts`.

Slices: `catalog` (ring 3, `voyager-platform`), `world` (ring 3, `voyager-platform`), `server.config` (composition root, `voyager-server`), and the run tasks in `voyager-server/build.gradle.kts`. Depends on `unify-catalog-loading`, which supplies the single load point and the collect-every-problem report this change reuses. Roadmap Q6 in research 005 also lists Q4 (`scope-cup-validation`) as a dependency; this change does not require it, and the cup-selection rule follows whatever Q4 decides once it lands.

## Why

NFR-007 requires that every configuration error is reported together and that the server can validate and exit without binding a socket. Neither exists in source (research 005, P10 and E3). Today an operator learns of one problem per boot: a missing world directory stops `ServerSettings` at the first missing path, a bad map file stops the catalogue at the first bad file, and a missing world surfaces only when a map is first entered. A run task that merely warns about a missing worlds directory and then starts the server is the same failure in a different place.

## What Changes

- Add a validate-and-exit mode to the server: `-Dvoyager.config.check=true` loads the catalogue, checks every referenced world folder and the resolved settings, prints every problem with its file and field, and exits 0 when there are no errors or 1 when there is at least one. It never binds a socket and never starts Minestom.
- Add the Gradle task `:voyager-server:validateCatalog`, which runs that mode against the same directories the run tasks use (`run/run/data` and `run/run/worlds`, or the `-PdataPath` and `-PworldsPath` overrides).
- Make `runServer` and `runServerDev` depend on `validateCatalog`, so a run with errors stops before the JVM starts the server. **BREAKING (developer workflow):** a run with a missing worlds directory or an invalid catalogue now fails at the validation step instead of logging a warning and starting the server.
- Add `ConfigProblem(key, source, message)` to `voyager-api` (the record NFR-007 names). Every problem the check reports is one `ConfigProblem`, sorted so that two runs on two machines print the same lines.
- Report, for every map, whether its `world` folder exists under the worlds directory and holds region data, using the same layout rule `MapInstances` applies (`region/` or `dimensions/`), extracted into a Minestom-free `WorldFolders` so both callers share it.
- Report settings problems (unknown or unreadable data and worlds directories, a non-numeric or out-of-range port, a `VOYAGER_CUP` naming a cup that does not exist) without throwing on the first one. `ServerSettings`' compact constructor keeps its refusal for the normal boot path.
- Keep the normal boot path unchanged: a server started without the check property still refuses an invalid configuration, now with the same aggregate report.
- Out of scope: the deep world check (`WorldHealth` via Falco, needs a Minestom bootstrap; proposed as a Could-have opt-in in this change, to be confirmed by the owner), any escape hatch that skips validation on run tasks, scoped cup validation (Q4), hot reload (Q7), and the setup slice (`voyager-setup`, not in the build).
- Out of scope, separate change: `prepareRunData` uses `Copy`, so a map deleted from `src/main/resources/maps` keeps being served from `run/run/data`. The fix (`Copy` to `Sync`) is a `fix(build)` commit, and the project rule is one type per change, so it is proposed as its own change, `fix-run-data-sync`, to be created on the owner's approval. This change validates the directory the server actually reads, so a stale map is reported as valid until that fix lands.
- The doubled `run/run` path is kept unchanged. The check prints absolute paths so the doubling is visible in every report.

## Capabilities

### New Capabilities
- `catalog/catalog-validation`: checking that the catalogue and the worlds it references are complete and consistent, reporting every problem with its file and field, and failing as one unit.
- `server/config-check-mode`: the validate-and-exit run mode, its exit codes, its Gradle task, and the run tasks that depend on it.

### Modified Capabilities
- None. `openspec/specs/` holds no requirement on configuration or catalogues today.

## Impact

- **Code (api):** `ConfigProblem` record in a new `net.elytrarace.voyager.api.config` package with `package-info.java`.
- **Code (platform):** `CatalogValidation` in `net.elytrarace.voyager.platform.catalog`, built on the aggregate loader from `unify-catalog-loading`; `WorldFolders` in `net.elytrarace.voyager.platform.world`, extracted from `MapInstances.holdsRegionData`.
- **Code (server):** `ConfigCheck` in `net.elytrarace.voyager.server.config` (non-throwing settings checks plus the catalogue and world results); a `main` branch in `VoyagerServer` for `voyager.config.check`.
- **Build:** `voyager-server/build.gradle.kts` gains `validateCatalog` (`JavaExec`, `dependsOn(prepareRunData)`), and both run tasks depend on it. The `doFirst` missing-worlds warning is replaced by the validator's error.
- **Fitness:** no new ArchUnit rule is proposed. `ApiPurityTest` already covers `ConfigProblem`. The validator's no-Minestom property is checked by a unit test, not by a rule; a rule is a follow-up if the owner wants one.
- **Docs:** `docs/reference` gets the check's exit codes and output format; research 005 Q6 and P10 status are updated in the same PR as the code.
- **Runtime:** none on a valid configuration. A run with problems stops earlier and prints the full report.
- **Dependencies:** `unify-catalog-loading` (must land first). Roadmap Q4 (`scope-cup-validation`) is related and not required.
