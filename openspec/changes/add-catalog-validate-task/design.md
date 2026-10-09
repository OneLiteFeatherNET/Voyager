# Design

## Dependency direction

```
voyager-server  (composition root, ring 4)
   ├── server.config.ConfigCheck  ──┐  settings checks, entry for voyager.config.check, normal-boot report
   │                                 ▼
voyager-platform (ring 3, adapters)
   ├── catalog.CatalogValidation ─── uses CatalogLoader.read (unify-catalog-loading)
   ├── world.WorldFolders        ─── layout rule, shared with MapInstances
   └── world.MapInstances.healthOf ─ deep world health (Falco), already in platform
                                 ▼
voyager-api  (ring 1-2, no I/O)
   └── api.config.ConfigProblem(key, source, message, severity)
```

Arrows point inward. `voyager-platform` never imports `voyager-server`. `ConfigProblem` has no I/O, so it lives in `voyager-api`.

## Where each type lives

| Type | Module | Package | Why there |
|---|---|---|---|
| `ConfigProblem` (record, severity enum nested) | voyager-api | `net.elytrarace.voyager.api.config` | Pure record named by NFR-007; used by platform and server. |
| `WorldFolders` | voyager-platform | `net.elytrarace.voyager.platform.world` | Path rule only, no Minestom. `MapInstances.holdsRegionData` delegates to it. |
| `CatalogValidation` | voyager-platform | `net.elytrarace.voyager.platform.catalog` | Needs Gson (catalogue), the filesystem and `MapInstances` for the deep check; `onlyPlatformDependsOnGson` already allows it. |
| `ConfigCheck` | voyager-server | `net.elytrarace.voyager.server.config` | Reads system properties; the only place that knows the property names. |

## Decisions

1. **One entry point, two triggers.** `VoyagerServer.main` checks `voyager.config.check` first and returns before the game
   server is started or any socket is bound. The Gradle task `validateCatalog` is a `JavaExec` on the runtime classpath with
   that property set. No second main class.
   - Alternative: a separate `ConfigCheckMain`. Rejected: two entry points for one check drift apart.
2. **Collect, do not throw.** The catalogue half turns each problem of `CatalogLoader.read` (unify-catalog-loading) into a
   `ConfigProblem`. The settings half is a new non-throwing function. `ServerSettings`' compact constructor is not changed;
   it keeps the refusal for the library-level path.
   - The normal boot path (no check property) also refuses with this full report, as the owner allowed for this change.
     Boot therefore lists every problem at once, while the catalogue's own `load` (unify) still throws the first problem
     for any caller that is not the server's boot path.
   - Consequence: the throwing constructor and the check must agree on what is an error. A shared private predicate, not two
     copies, is required (task 3.2).
3. **Deep by default: the world check reads region data through the server's loader.** Every map's world is checked in two
   steps:
   - Shallow (cheap, always): the folder exists under the worlds directory and holds region data for the layout `MapInstances`
     reads (`region/` or `dimensions/`). A missing folder or no region data is an error, and the deep step is skipped for
     that world.
   - Deep (approved 2026-10-09): `MapInstances.healthOf(world)` reports a `WorldHealth`; a world that is not `isSound()` is an
     error, and the problem names the counters that failed (`WorldHealth.describe()`). `isSound()` is false for a world with no
     chunk read, so the deep step reads chunks on purpose; a world with refused, unknown or failed chunks is an error.
   - **Needs no client.** No player connects and no tick loop runs. It does need Minestom's registries, because Falco attaches
     to a Minestom `Instance`. The check therefore calls the registry initialisation of Minestom and nothing else. No socket
     is bound. This relaxes the earlier wording "never starts Minestom" to "never starts the server".
   - **Runtime cost.** Not measured when this design was written. Each deep check reads every chunk the check selects from the
     world's region files, so its time grows with the number of region files. Reference size: the shipped world
     `ElytraraceBlueAndRed` holds 9,429 chunks (`MapInstances` javadoc). Task 4.6 measures the wall time of `validateCatalog`
     on that world and records it here before the PR opens. If the measured cost is more than the owner accepts for a run
     task, the owner chooses between the skip switch (already approved) and narrowing the chunk set to the chunks that cover
     each map's rings and spawn. Narrowing is not decided here.
   - Failure policy: a deep-check exception (for example Falco failing to open a region file) becomes an `ERROR` for that world
     with the exception's message, and the other worlds are still checked.
4. **Exit codes.** 0 for no errors, including warnings. 1 for at least one error. No other codes, so CI can rely on the two values.
5. **Output.** One line per problem: `ERROR|WARN <absolute source> <key>: <message>`, sorted by source then key. Stdout for the
   report, stderr for nothing else.
6. **Run tasks and the skip switch.** `runServer` and `runServerDev` depend on `validateCatalog`, which depends on
   `prepareRunData`, so the Copy-to-Sync fix runs first. The skip switch `-PskipCatalogCheck` (approved 2026-10-09) removes only
   the run tasks' dependency on `validateCatalog`: `tasks.named("runServer") { if (!skip) dependsOn(validateCatalog) }`. A skipped
   run logs one `WARN` line, `catalogue check skipped by -PskipCatalogCheck`. `./gradlew :voyager-server:validateCatalog` runs
   the check regardless of the flag.
   - Cost: one extra JVM start per run plus the deep check time (decision 3). Accepted, because the alternative is a warning
     that the operator must read.
7. **Path constants.** The Gradle task reads `runDataDir` and `runWorldsDir` from `voyager-server/build.gradle.kts`, the same values
   the run tasks use, so the build and the runtime cannot disagree on the doubled path.
8. **Merge order.** `fix-run-data-sync` first (see proposal). Without it, this check validates a stale copy of the catalogue.

## Risks

- `unify-catalog-loading` changes `JsonMapCatalog` and `JsonCupCatalog`. This change must rebase onto it, not onto the current
  classes.
- Two cup-validation rules (this change's report and Q4's scoped rule) could disagree on which cups are errors. Until Q4 lands, all
  cups are validated as errors, matching today's cross-catalogue check.
- **The deep check may be slow** (decision 3). Mitigation: measured in task 4.6; the skip switch exists; narrowing is an owner
  choice.
- **Falco and Minestom registries at check time.** If the registry initialisation is heavier than expected, the check start-up
  grows by that amount. Measured together with the deep-check cost in task 4.6.

## Resolved questions (owner, 2026-10-09)

| Question | Decision |
|---|---|
| Deep world check: Could-have opt-in, or dropped? | Part of `validateCatalog` (decision 3). Costs recorded and measured. |
| Escape hatch for run tasks? | `-PskipCatalogCheck` for the run tasks only (decision 6). |
| Create `fix-run-data-sync` and land it first? | Yes. It is a separate change (`fix(build)`) and lands before this PR (proposal, merge order). |

## ADR

None yet. The exit-code and run-gating decision is recorded here. If the owner wants it as a MADR in `docs/decisions/`, that is a
separate task.
